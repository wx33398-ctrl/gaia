package com.xwang.gaia.core.base;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xwang.gaia.core.exception.BusinessException;
import com.xwang.gaia.core.util.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.BeanUtil;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Controller统一封装抽象，自带8个通用接口
 * 单个新增、批量新增
 * 单个修改、批量修改
 * 单个删除、批量删除
 * 详情查询、分页查询
 *
 * @param <E> Entity
 * @param <D> DTO
 * @param <V> VO
 * @author xwang
 */
@Slf4j
public abstract class BaseController<E, D extends BaseDTO, V extends BaseVO> {

	private final Class<E> classE;
	private final Class<V> classV;

	/**
	 * 构造期解析泛型类型，避免运行时反射和线程安全问题
	 */
	protected BaseController() {
		ParameterizedType superClass = (ParameterizedType) getClass().getGenericSuperclass();
		this.classE = (Class<E>) superClass.getActualTypeArguments()[0];
		this.classV = (Class<V>) superClass.getActualTypeArguments()[2];
	}

	/**
	 * 获取service实现类
	 *
	 * @return
	 */
	protected abstract BaseService<E, D> getService();

	/**
	 * 单个新增
	 *
	 * @param dto
	 * @return 返回新增对象的主键ID
	 */
	public R<Object> addEntity(D dto) {
		return R.success(getService().addEntity(dto));
	}

	/**
	 * 批量新增
	 *
	 * @param list
	 * @return 返回批量插入的list的size
	 */
	public R<Integer> addBatch(Collection<D> list) {
		if (!CollectionUtils.isEmpty(list)) {
			getService().addBatch(list.stream().map(this::convertToEntity).collect(Collectors.toList()));
		}
		return R.success(CollectionUtils.isEmpty(list) ? 0 : list.size());
	}

	/**
	 * 查询列表（带分页）
	 *
	 * @param dto
	 * @return
	 */
	public R<IPage<V>> queryPage(D dto) {
		return R.success(getService().queryPage(dto, null), newInstanceV()::of);
	}

	/**
	 * 查询详情
	 * 按ID查询不到记录报错
	 *
	 * @param id
	 * @return
	 */
	public R<V> queryDetail(Serializable id) {
		E e = getService().queryDetail(id);
		if (e == null) {
			return R.failure(BusinessException.Status.NOT_FOUND_RECORD);
		} else {
			return R.success((V)newInstanceV().of(e));
		}
	}

	/**
	 * 修改对象接口
	 *
	 * @param dto
	 * @return
	 */
	public R<Void> modifyEntity(D dto) {
		if (getService().modifyEntity(dto)) {
			return R.success();
		} else {
			return R.failure(BusinessException.Status.DATA_UPDATE_ERROR);
		}
	}

	/**
	 * 根据ID 批量更新
	 *
	 * @param list
	 * @return
	 */
	public R<Integer> modifyBatch(List<D> list) {
		if (!CollectionUtils.isEmpty(list)) {
			getService().updateBatchById(list.stream().map(this::convertToEntity).collect(Collectors.toList()));
		}
		return R.success(CollectionUtils.isEmpty(list) ? 0 : list.size());
	}

	/**
	 * 根据ID删除单个对象接口
	 *
	 * @param id
	 * @return
	 */
	public R<Void> removeEntity(Serializable id) {
		if (getService().removeEntity(id)) {
			return R.success();
		} else {
			return R.failure(BusinessException.Status.DATA_DELETE_ERROR);
		}
	}

	/**
	 * 根据idList批量删除接口
	 *
	 * @param idList
	 * @return 删除的记录数
	 */
	public R<Integer> removeBatch(List<Serializable> idList) {
		int num = getService().removeBatch(idList);
		if (num > 0) {
			return R.success(num);
		} else {
			return R.failure(BusinessException.Status.DATA_DELETE_ERROR);
		}
	}

	/**
	 * 获取Entity类型
	 *
	 * @return
	 */
	public Class<E> getClassE() {
		return classE;
	}

	/**
	 * 获取VO类型
	 *
	 * @return
	 */
	public Class<V> getClassV() {
		return classV;
	}

	/**
	 * 创建VO实例
	 *
	 * @return
	 */
	private V newInstanceV() {
		try {
			return classV.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new BusinessException(BusinessException.Status.BEAN_COPY_ERROR);
		}
	}

	/**
	 * DTO转化成Entity
	 *
	 * @param d
	 * @return
	 */
	public E convertToEntity(D d) {
		E e = newInstanceE();
		BeanUtil.copy(d, e);
		return e;
	}

	/**
	 * 创建Entity实例
	 *
	 * @return
	 */
	private E newInstanceE() {
		try {
			return classE.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new BusinessException(BusinessException.Status.BEAN_COPY_ERROR);
		}
	}

	/**
	 * 生成page查询缓存key,过滤空属性。
	 * TreeMap 按字段名排序保证与声明顺序无关；显式 UTF-8 保证不随 JVM 默认字符集漂移
	 *
	 * @param dto
	 * @return
	 */
	public String getCacheKey(D dto) {
		Map<String, Object> nonNull = new TreeMap<>();
		for (Field f : getAllFields(dto.getClass())) {
			f.setAccessible(true);
			try {
				Object v = f.get(dto);
				if (v != null && !(v instanceof String && ((String) v).isEmpty())) {
					nonNull.put(f.getName(), v);
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		// shortcut: TreeMap.toString 理论上有键值拼接歧义，查询参数场景碰撞可忽略；真出现缓存误命中再升级为转义拼接
		return DigestUtils.md5DigestAsHex(nonNull.toString().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 生成page查询缓存key,过滤空属性
	 * @param dto
	 * @param str 预留给租户ID或者部门ID
	 * @return
	 */
	public String getCacheKey(D dto, String str){
		String cacheKey = str + getCacheKey(dto);
		log.info("cacheKey={}", cacheKey);
		return cacheKey;
	}

	private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

	private static List<Field> getAllFields(Class<?> clazz) {
		return FIELD_CACHE.computeIfAbsent(clazz, k -> {
			List<Field> fields = new ArrayList<>();
			Class<?> c = k;
			while (c != null) {
				for (Field f : c.getDeclaredFields()) {
					if ((f.getModifiers() & Modifier.STATIC) == 0 && (f.getModifiers() & Modifier.TRANSIENT) == 0) {
						fields.add(f);
					}
				}
				c = c.getSuperclass();
			}
			return fields;
		});
	}

	/**
	 * 获取当前请求的request对象
	 * @return
	 */
	protected HttpServletRequest getRequest(){
		return ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getRequest();
	}

	/**
	 * 获取当前请求的response对象
	 * @return
	 */
	protected HttpServletResponse getResponse(){
		return ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getResponse();
	}
}

package com.xwang.gaia.core.base;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xwang.gaia.core.util.SqlInjectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.extension.conditions.query.ChainQuery;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.ChainUpdate;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import net.dreamlu.mica.core.utils.BeanUtil;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.session.SqlSession;
import lombok.Getter;
import lombok.ToString;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用服务接口，默认提供常用接口
 *
 * @param <E>
 * @param <D>
 * @author xwang
 */
public interface BaseService<E, D extends BaseDTO> {

	/**
	 * 获取mapper
	 *
	 * @return
	 */
	BaseMapper<E> getMapper();

	/**
	 * 获取Log
	 *
	 * @return
	 */
	Log getLog();

	/**
	 * 应用排序参数，校验SQL注入风险。
	 * 输出排序列统一加主表别名 t. 前缀：注入的 selectById/selectPage/selectList/selectCount
	 * 主表恒有别名 t（含 @Relation 联表场景），裸列名在 join 下会 1052 ambiguous。
	 * orders 列在校验后就地改写为 t. 前缀（其经 Page 直达 MP 分页拦截器原样拼 ORDER BY）。
	 */
	default void applySorting(QueryWrapper<E> query, D dto) {
		// ascs/descs 独立生效（shop-admin 业务依赖两向组合排序，如商品列表 sort_order ASC + id DESC）
		if (!StrUtil.isEmpty(dto.getAscs())) {
			String fields = StrUtil.toSymbolCase(dto.getAscs(), '_');
			if (SqlInjectionUtils.containsSqlInjection(fields)) {
				throw new IllegalArgumentException("Invalid sort field: " + fields);
			}
			// List 重载逐列拼方向；整串传入会拼成 `a,b ASC`，除末列外方向全部丢失
			query.orderByAsc(prefixedColumns(fields));
		}
		if (!StrUtil.isEmpty(dto.getDescs())) {
			String fields = StrUtil.toSymbolCase(dto.getDescs(), '_');
			if (SqlInjectionUtils.containsSqlInjection(fields)) {
				throw new IllegalArgumentException("Invalid sort field: " + fields);
			}
			query.orderByDesc(prefixedColumns(fields));
		}
		// D 的上界是裸 BaseDTO，getOrders() 被擦除为原始 List，此处局部收口为类型化引用
		List<OrderItem> orders = dto.getOrders();
		if (orders != null) {
			for (OrderItem order : orders) {
				if (StrUtil.isNotEmpty(order.getColumn())) {
					String field = StrUtil.toSymbolCase(order.getColumn(), '_');
					if (SqlInjectionUtils.containsSqlInjection(field)) {
						throw new IllegalArgumentException("Invalid sort field: " + field);
					}
					order.setColumn("t." + field);
				}
			}
		}
	}

	/** 校验通过的逗号列串 → 带主表别名 t. 前缀的列 List */
	private static List<String> prefixedColumns(String fields) {
		return Arrays.stream(fields.split(",")).map(f -> "t." + f).toList();
	}

	/**
	 * 新增单个对象
	 *
	 * @param dto
	 * @return
	 */
	default Object addEntity(D dto) {
		E entity = convertToEntityForAdd(dto);
		getMapper().insert(entity);
		return ReflectionKit.getFieldValue(entity, TableInfoHelper.getTableInfo(entity.getClass()).getKeyProperty());
	}

	/**
	 * 按分页查询
	 *
	 * @param dto
	 * @return
	 */
	default IPage<E> queryPage(D dto) {
		QueryWrapper<E> query = new QueryWrapper<>();
		if (dto == null) {
			dto = newInstanceD();
		} else {
				query.setEntity(convertToEntityForQuery(dto));
		}
		applySorting(query, dto);
		return getMapper().selectPage(dto.getPage(), query);
	}

	/**
	 * 按分页查询
	 *
	 * @param dto
	 * @return
	 */
	default IPage<E> queryPage(D dto, Consumer<QueryWrapper<E>> consumer) {
		QueryWrapper<E> query = new QueryWrapper<>();
		if (dto == null) {
			dto = newInstanceD();
		} else {
				query.setEntity(convertToEntityForQuery(dto));
		}
		applySorting(query, dto);
		if (consumer != null) {
			consumer.accept(query);
		}
		return getMapper().selectPage(dto.getPage(), query);
	}

	/**
	 * maxLimit 钳制上限：客户端可传大值，但设硬顶，防止超大 LIMIT 拖垮数据库
	 */
	long MAX_LIMIT = 50_000L;

	/**
	 * maxLimit 下推为 SQL LIMIT（替代全量拉取后内存截断），钳制 {@link #MAX_LIMIT}；
	 * <b>未传 maxLimit 时默认追加 {@code LIMIT BaseDTO.MAX_PAGE_SIZE}（1000）</b>，
	 * 封堵"无界条件查询全表拉取"的 DoS 路径（宿主契约：queryList 必带兜底 LIMIT）。
	 */
	default void applyMaxLimit(QueryWrapper<E> query, D dto) {
		// lastSql 拼接在 getSqlSegment() 尾部使其变长，据此探测 consumer 是否已设过 last()
		boolean hasLast = query.getSqlSegment().length() > query.getExpression().getSqlSegment().length();
		Long requested = dto.getMaxLimit();
		if (requested != null && requested > 0) {
			String last = "LIMIT " + Math.min(requested, MAX_LIMIT);
			// MP 的 last() 是整体替换语义：consumer 已设过 last() 时被静默覆盖，显式告警
			if (hasLast) {
				getLog().warn("maxLimit 与 queryWrapper 已有 last() 冲突，本次设置生效: " + last);
			}
			query.last(last);
		} else if (!hasLast) {
			// 默认兜底；consumer 显式设过 last() 视为调用方自行负责，不再叠加
			query.last("LIMIT " + BaseDTO.MAX_PAGE_SIZE);
		}
	}

	/**
	 * 列表查询
	 *
	 * @param dto
	 * @return
	 */
	default List<E> queryList(D dto, Consumer<QueryWrapper<E>> consumer) {
		QueryWrapper<E> query = Wrappers.query();
		if (dto == null) {
			dto = newInstanceD();
		} else {
				query.setEntity(convertToEntityForQuery(dto));
		}

		applySorting(query, dto);
		if (consumer != null) {
			consumer.accept(query);
		}
		applyMaxLimit(query, dto);
		return getMapper().selectList(query);
	}

	/**
	 * 列表查询
	 *
	 * @param dto
	 * @return
	 */
	default List<E> queryList(D dto) {
		QueryWrapper<E> query = Wrappers.query();
		if (dto == null) {
			dto = newInstanceD();
		} else {
				query.setEntity(convertToEntityForQuery(dto));
		}

		applySorting(query, dto);
		applyMaxLimit(query, dto);
		return getMapper().selectList(query);
	}

	/**
	 * 列表查询
	 *
	 * @param queryWrapper
	 * @return
	 */
	default List<E> queryList(Wrapper<E> queryWrapper) {
		if (queryWrapper != null) {
			return getMapper().selectList(queryWrapper);
		}
		return Collections.emptyList();
	}


	/**
	 * count查询
	 *
	 * @param dto
	 * @return
	 */
	default Long queryCount(D dto) {
		QueryWrapper<E> query = Wrappers.query();
		query.setEntity(convertToEntityForQuery(dto));
		return getMapper().selectCount(query);
	}

	/**
	 * 详情查询
	 *
	 * @param id
	 * @return
	 */
	default E queryDetail(Serializable id) {
		return getMapper().selectById(id);
	}

	/**
	 * 查询一条记录，多条记录报错，支持联合主键的场景
	 *
	 * @param dto
	 * @return
	 */
	default E queryOne(D dto) {
		QueryWrapper<E> query = Wrappers.query();
		query.setEntity(convertToEntityForQuery(dto));
		return getMapper().selectOne(query);
	}

	/**
	 * 单个修改，dto中必须包含ID
	 *
	 * @param dto
	 * @return
	 */
	default boolean modifyEntity(D dto) {
		return getMapper().updateById(convertToEntityForModify(dto)) > 0;
	}

	/**
	 * 根据ID单个删除
	 *
	 * @param id
	 * @return
	 */
	default boolean removeEntity(Serializable id) {
		return getMapper().deleteById(id) > 0;
	}

	/**
	 * 根据条件删除(可删除多个)
	 *
	 * @param dto
	 * @return
	 */
	default int removeEntity(D dto) {
		QueryWrapper<E> query = Wrappers.query();
		query.setEntity(convertToEntityForQuery(dto));
		return getMapper().delete(query);
	}

	/**
	 * 批量删除
	 *
	 * @param idList
	 * @return
	 */
	default int removeBatch(Collection<? extends Serializable> idList) {
		return getMapper().deleteBatchIds(idList);
	}

	/**
	 * 查询场景下的数据转换
	 *
	 * @param dto
	 * @return
	 */
	default E convertToEntityForQuery(D dto) {
		return convertToEntity(dto);
	}

	/**
	 * 新增场景下的数据转换
	 *
	 * @param dto
	 * @return
	 */
	default E convertToEntityForAdd(D dto) {
		return convertToEntity(dto);
	}

	/**
	 * 修改场景下的数据转换
	 *
	 * @param dto
	 * @return
	 */
	default E convertToEntityForModify(D dto) {
		return convertToEntity(dto);
	}

	/**
	 * DTO转化成Entity
	 *
	 * @param dto
	 * @return
	 */
	default E convertToEntity(D dto) {
		E entity = newInstanceE();
		BeanUtil.copy(dto, entity);
		return entity;
	}

	/**
	 * 创建Entity
	 *
	 * @return
	 */
	default E newInstanceE() {
		Constructor<E> ctor = getTypeCache().constructorE;
		if (ctor == null) throw new IllegalArgumentException("No default constructor for " + getTypeCache().classE.getName());
		try {
			return ctor.newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("create entity bean error");
		}
	}

	/**
	 * 创建Dto
	 *
	 * @return
	 */
	default D newInstanceD() {
		Constructor<D> ctor = getTypeCache().constructorD;
		if (ctor == null) throw new IllegalArgumentException("No default constructor for " + getTypeCache().classD.getName());
		try {
			return ctor.newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("create dto bean error");
		}
	}

	/**
	 * 获取实体Class
	 *
	 * @return
	 */
	default Class<E> getClassE() {
		return getTypeCache().classE;
	}

	//***********兼容Myabtis-Plus IService************//

	/**
	 * 默认批次提交数量
	 */
	int DEFAULT_BATCH_SIZE = 1000;


	/**
	 * 插入（批量）
	 *
	 * @param entityList 实体对象集合
	 */
	@Transactional(rollbackFor = Exception.class)
	default boolean addBatch(Collection<E> entityList) {
		return addBatch(entityList, DEFAULT_BATCH_SIZE);
	}

	/**
	 * 插入（批量）
	 *
	 * @param entityList 实体对象集合
	 * @param batchSize  插入批次数量
	 */
	@Transactional(rollbackFor = Exception.class)
	default boolean addBatch(Collection<E> entityList, int batchSize) {
		String sqlStatement = getSqlStatement(SqlMethod.INSERT_ONE);
		return executeBatch(entityList, batchSize, (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
	}


	/**
	 * 批量修改插入
	 *
	 * @param entityList 实体对象集合
	 */
	@Transactional(rollbackFor = Exception.class)
	default boolean saveOrUpdateBatch(Collection<E> entityList) {
		return saveOrUpdateBatch(entityList, DEFAULT_BATCH_SIZE);
	}

	/**
	 * 批量修改插入
	 *
	 * @param entityList 实体对象集合
	 * @param batchSize  每次的数量
	 */
	@Transactional(rollbackFor = Exception.class)
	default boolean saveOrUpdateBatch(Collection<E> entityList, int batchSize) {
		TableInfo tableInfo = TableInfoHelper.getTableInfo(getClassE());
		Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!");
		String keyProperty = tableInfo.getKeyProperty();
		Assert.notEmpty(keyProperty, "error: can not execute. because can not find column for id from entity!");
		return SqlHelper.saveOrUpdateBatch(getClassE(), getMapper().getClass().getInterfaces()[0], getLog(),
				entityList, batchSize,
				(sqlSession, entity) -> {
					Object idVal = ReflectionKit.getFieldValue(entity, keyProperty);
					return StringUtils.checkValNull(idVal)
							|| CollectionUtils.isEmpty(sqlSession.selectList(getSqlStatement(SqlMethod.SELECT_BY_ID),
							entity));
				}, (sqlSession, entity) -> {
					MapperMethod.ParamMap<E> param = new MapperMethod.ParamMap<>();
					param.put(Constants.ENTITY, entity);
					sqlSession.update(getSqlStatement(SqlMethod.UPDATE_BY_ID), param);
				});
	}

	/**
	 * 根据 columnMap 条件，删除记录
	 *
	 * @param columnMap 表字段 map 对象
	 */
	default boolean removeByMap(Map<String, Object> columnMap) {
		Assert.notEmpty(columnMap, "error: columnMap must not be empty");
		return SqlHelper.retBool(getMapper().deleteByMap(columnMap));
	}

	/**
	 * 根据 entity 条件，删除记录
	 *
	 * @param queryWrapper 实体包装类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default boolean remove(Wrapper<E> queryWrapper) {
		return SqlHelper.retBool(getMapper().delete(queryWrapper));
	}

	/**
	 * 根据 ID 选择修改
	 *
	 * @param entity 实体对象
	 */
	default boolean updateById(E entity) {
		return SqlHelper.retBool(getMapper().updateById(entity));
	}


	/**
	 * 根据 UpdateWrapper 条件，更新记录 需要设置sqlset
	 *
	 * @param updateWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper}
	 */
	default boolean update(Wrapper<E> updateWrapper) {
		return update(null, updateWrapper);
	}

	/**
	 * 根据 whereEntity 条件，更新记录
	 *
	 * @param entity        实体对象
	 * @param updateWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper}
	 */
	default boolean update(E entity, Wrapper<E> updateWrapper) {
		return SqlHelper.retBool(getMapper().update(entity, updateWrapper));
	}

	/**
	 * 根据ID 批量更新
	 *
	 * @param entityList 实体对象集合
	 */
	@Transactional(rollbackFor = Exception.class)
	default boolean updateBatchById(Collection<E> entityList) {
		return updateBatchById(entityList, DEFAULT_BATCH_SIZE);
	}

	/**
	 * 根据ID 批量更新
	 *
	 * @param entityList 实体对象集合
	 * @param batchSize  更新批次数量
	 */
	@Transactional(rollbackFor = Exception.class)
	default boolean updateBatchById(Collection<E> entityList, int batchSize) {
		String sqlStatement = getSqlStatement(SqlMethod.UPDATE_BY_ID);
		return executeBatch(entityList, batchSize, (sqlSession, entity) -> {
			MapperMethod.ParamMap<E> param = new MapperMethod.ParamMap<>();
			param.put(Constants.ENTITY, entity);
			sqlSession.update(sqlStatement, param);
		});
	}

	/**
	 * TableId 注解存在更新记录，否插入一条记录
	 *
	 * @param entity 实体对象
	 */
	default boolean saveOrUpdate(E entity) {
		if (null != entity) {
			TableInfo tableInfo = TableInfoHelper.getTableInfo(getClassE());
			Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!");
			String keyProperty = tableInfo.getKeyProperty();
			Assert.notEmpty(keyProperty, "error: can not execute. because can not find column for id from entity!");
			Object idVal = ReflectionKit.getFieldValue(entity, tableInfo.getKeyProperty());
			return StringUtils.checkValNull(idVal) || Objects.isNull(getMapper().selectById((Serializable) idVal)) ?
					SqlHelper.retBool(getMapper().insert(entity)) : SqlHelper.retBool(getMapper().updateById(entity));
		}
		return false;
	}


	/**
	 * 查询（根据ID 批量查询）
	 *
	 * @param idList 主键ID列表
	 */
	default List<E> listByIds(Collection<? extends Serializable> idList) {
		return getMapper().selectBatchIds(idList);
	}

	/**
	 * 查询（根据 columnMap 条件）
	 *
	 * @param columnMap 表字段 map 对象
	 */
	default List<E> listByMap(Map<String, Object> columnMap) {
		return getMapper().selectByMap(columnMap);
	}

	/**
	 * 根据 Wrapper，查询一条记录 <br/>
	 * <p>结果集，如果是多个会抛出异常，随机取一条加上限制条件 wrapper.last("LIMIT 1")</p>
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default E getOne(Wrapper<E> queryWrapper) {
		return getOne(queryWrapper, true);
	}

	/**
	 * 根据 Wrapper，查询一条记录
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 * @param throwEx      有多个 result 是否抛出异常
	 */
	default E getOne(Wrapper<E> queryWrapper, boolean throwEx) {
		if (throwEx) {
			return getMapper().selectOne(queryWrapper);
		}
		return SqlHelper.getObject(getLog(), getMapper().selectList(queryWrapper));
	}

	/**
	 * 根据 Wrapper，查询一条记录
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default Map<String, Object> getMap(Wrapper<E> queryWrapper) {
		return SqlHelper.getObject(getLog(), getMapper().selectMaps(queryWrapper));
	}

	/**
	 * 根据 Wrapper，查询一条记录
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 * @param mapper       转换函数
	 */
	default <V> V getOneId(Wrapper<E> queryWrapper, Function<? super Object, V> mapper) {
		return SqlHelper.getObject(getLog(), listIds(queryWrapper, mapper));
	}

	/**
	 * 查询总记录数
	 *
	 * @see Wrappers#emptyWrapper()
	 */
	default long count() {
		return count(Wrappers.emptyWrapper());
	}

	/**
	 * 根据 Wrapper 条件，查询总记录数
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default long count(Wrapper<E> queryWrapper) {
		return SqlHelper.retCount(getMapper().selectCount(queryWrapper));
	}

	/**
	 * 翻页查询
	 *
	 * @param page         翻页对象
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default <P extends IPage<E>> P page(P page, Wrapper<E> queryWrapper) {
		return getMapper().selectPage(page, queryWrapper);
	}

	/**
	 * 无条件翻页查询
	 *
	 * @param page 翻页对象
	 * @see Wrappers#emptyWrapper()
	 */
	default <P extends IPage<E>> P page(P page) {
		return page(page, Wrappers.emptyWrapper());
	}


	/**
	 * 查询列表
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default List<Map<String, Object>> listMaps(Wrapper<E> queryWrapper) {
		return getMapper().selectMaps(queryWrapper);
	}

	/**
	 * 查询所有列表
	 *
	 * @see Wrappers#emptyWrapper()
	 */
	default List<Map<String, Object>> listMaps() {
		return listMaps(Wrappers.emptyWrapper());
	}

	/**
	 * 查询全部记录
	 */
	default List<Object> listIds() {
		return listIds(Function.identity());
	}

	/**
	 * 查询全部记录
	 *
	 * @param mapper 转换函数
	 */
	default <V> List<V> listIds(Function<? super Object, V> mapper) {
		return listIds(Wrappers.emptyWrapper(), mapper);
	}

	/**
	 * 根据 Wrapper 条件，查询全部记录
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default List<Object> listIds(Wrapper<E> queryWrapper) {
		return listIds(queryWrapper, Function.identity());
	}

	/**
	 * 根据 Wrapper 条件，查询全部记录
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 * @param mapper       转换函数
	 */
	default <V> List<V> listIds(Wrapper<E> queryWrapper, Function<? super Object, V> mapper) {
		return getMapper().selectObjs(queryWrapper).stream().filter(Objects::nonNull).map(mapper).collect(Collectors.toList());
	}

	/**
	 * 查询列表
	 *
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default List<E> list(Wrapper<E> queryWrapper) {
		return getMapper().selectList(queryWrapper);
	}

	/**
	 * 翻页查询
	 *
	 * @param page         翻页对象
	 * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
	 */
	default <P extends IPage<Map<String, Object>>> P pageMaps(P page, Wrapper<E> queryWrapper) {
		return getMapper().selectMapsPage(page, queryWrapper);
	}

	/**
	 * @param page
	 * @param <P>
	 * @return
	 */
	default <P extends IPage<Map<String, Object>>> P pageMaps(P page) {
		return pageMaps(page, Wrappers.emptyWrapper());
	}

	/**
	 * 以下的方法使用介绍:
	 *
	 * 一. 名称介绍
	 * 1. 方法名带有 query 的为对数据的查询操作, 方法名带有 update 的为对数据的修改操作
	 * 2. 方法名带有 lambda 的为内部方法入参 column 支持函数式的
	 * 二. 支持介绍
	 *
	 * 1. 方法名带有 query 的支持以 {@link ChainQuery} 内部的方法名结尾进行数据查询操作
	 * 2. 方法名带有 update 的支持以 {@link ChainUpdate} 内部的方法名为结尾进行数据修改操作
	 *
	 * 三. 使用示例,只用不带 lambda 的方法各展示一个例子,其他类推
	 * 1. 根据条件获取一条数据: `query().eq("column", value).one()`
	 * 2. 根据条件删除一条数据: `update().eq("column", value).remove()`
	 *
	 */

	/**
	 * 链式查询 普通
	 *
	 * @return QueryWrapper 的包装类
	 */
	default QueryChainWrapper<E> query() {
		return ChainWrappers.queryChain(getMapper());
	}

	/**
	 * 链式查询 lambda 式
	 * <p>注意：不支持 Kotlin </p>
	 *
	 * @return LambdaQueryWrapper 的包装类
	 */
	default LambdaQueryChainWrapper<E> lambdaQuery() {
		return ChainWrappers.lambdaQueryChain(getMapper());
	}

	/*    *//**
	 * 链式查询 lambda 式
	 * kotlin 使用
	 *
	 * @return KtQueryWrapper 的包装类
	 *//*
    default KtQueryChainWrapper<E> ktQuery() {
        return ChainWrappers.ktQueryChain(getMapper(), newInstanceE());
    }

    *//**
	 * 链式查询 lambda 式
	 * kotlin 使用
	 *
	 * @return KtQueryWrapper 的包装类
	 *//*
    default KtUpdateChainWrapper<E> ktUpdate() {
        return ChainWrappers.ktUpdateChain(getMapper(), newInstanceE());
    }*/

	/**
	 * 链式更改 普通
	 *
	 * @return UpdateWrapper 的包装类
	 */
	default UpdateChainWrapper<E> update() {
		return ChainWrappers.updateChain(getMapper());
	}

	/**
	 * 链式更改 lambda 式
	 * <p>注意：不支持 Kotlin </p>
	 *
	 * @return LambdaUpdateWrapper 的包装类
	 */
	default LambdaUpdateChainWrapper<E> lambdaUpdate() {
		return ChainWrappers.lambdaUpdateChain(getMapper());
	}

	/**
	 * <p>
	 * 根据updateWrapper尝试更新，否继续执行saveOrUpdate(T)方法
	 * 此次修改主要是减少了此项业务代码的代码量（存在性验证之后的saveOrUpdate操作）
	 * </p>
	 *
	 * @param entity 实体对象
	 */
	default boolean saveOrUpdate(E entity, Wrapper<E> updateWrapper) {
		return update(entity, updateWrapper) || saveOrUpdate(entity);
	}

	/**
	 * 获取mapperStatementId
	 *
	 * @param sqlMethod 方法名
	 * @return 命名id
	 * @since 3.4.0
	 */
	default String getSqlStatement(SqlMethod sqlMethod) {
		return SqlHelper.getSqlStatement((Class<?>) getMapper().getClass().getGenericInterfaces()[0], sqlMethod);
	}

	/**
	 * 执行批量操作（默认批次提交数量{@link IRepository #DEFAULT_BATCH_SIZE}）
	 *
	 * @param list     数据集合
	 * @param consumer 执行方法
	 * @param <P>      泛型
	 * @return 操作结果
	 * @since 3.3.1
	 */
	default <P> boolean executeBatch(Collection<P> list, BiConsumer<SqlSession, P> consumer) {
		return executeBatch(list, DEFAULT_BATCH_SIZE, consumer);
	}

	/**
	 * 执行批量操作
	 *
	 * @param list      数据集合
	 * @param batchSize 批量大小
	 * @param consumer  执行方法
	 * @param <P>       泛型
	 * @return 操作结果
	 * @since 3.3.1
	 */
	default <P> boolean executeBatch(Collection<P> list, int batchSize, BiConsumer<SqlSession, P> consumer) {
		return SqlHelper.executeBatch(getClassE(), getLog(), list, batchSize, consumer);
	}

	/**
	 * 清楚缓存
	 */
	default void clearCache() {
	}

	Map<Class<?>, TypeCache<?, ?>> TYPE_CACHE = new ConcurrentHashMap<>();

	@SuppressWarnings("unchecked")
	default TypeCache<E, D> getTypeCache() {
		return (TypeCache<E, D>) TYPE_CACHE.computeIfAbsent(getClass(), k -> {
			ParameterizedType superClass = (ParameterizedType) ((Class) k.getGenericInterfaces()[0]).getGenericInterfaces()[0];
			Class<E> classE = (Class<E>) superClass.getActualTypeArguments()[0];
			Class<D> classD = (Class<D>) superClass.getActualTypeArguments()[1];
			return new TypeCache<>(classE, classD);
		});
	}

	@Getter
	@ToString
	class TypeCache<E, D> {
		private final Class<E> classE;
		private final Class<D> classD;
		private Constructor<E> constructorE;
		private Constructor<D> constructorD;

		@SuppressWarnings("unchecked")
		public TypeCache(Class<E> classE, Class<D> classD) {
			this.classE = classE;
			this.classD = classD;
			try {
				this.constructorE = classE.getDeclaredConstructor();
			} catch (Exception e) {
				this.constructorE = null;
			}
			try {
				this.constructorD = classD.getDeclaredConstructor();
			} catch (Exception e) {
				this.constructorD = null;
			}
		}
	}
}

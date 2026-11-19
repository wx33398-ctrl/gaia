package com.xwang.gaia.core.mybatisplus.injector;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xwang.gaia.core.mybatisplus.method.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义SqlInjector
 * 修改sql拼装行为，替换默认 SelectById/SelectPage/SelectList/SelectCount 为 Gaia 增强版本；
 * 可选注入数据权限 ByScope 查询（按构造参数给定的 Mapper 基接口全限定名识别）。
 *
 * @author xwang
 */
public class MySqlInjector extends DefaultSqlInjector {

	/**
	 * 需要替换的默认方法名
	 */
	private static final String[] REPLACED_METHODS = {"SelectById", "SelectPage", "SelectList", "SelectCount"};

	/**
	 * 数据权限 Mapper 基接口全限定名；null/空 = 关闭 ByScope 注入
	 */
	private final String datascopeMapperClass;

	/**
	 * 不启用 ByScope 的缺省形态
	 */
	public MySqlInjector() {
		this(null);
	}

	public MySqlInjector(String datascopeMapperClass) {
		this.datascopeMapperClass = datascopeMapperClass;
	}

	@Override
	public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
		List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);

		// 过滤掉默认的 SelectById/SelectPage/SelectList/SelectCount，用自定义版本替换
		methodList = methodList.stream().filter(s -> {
			String simpleName = s.getClass().getSimpleName();
			return !Arrays.stream(REPLACED_METHODS).anyMatch(name -> StringUtils.equals(simpleName, name));
		}).collect(Collectors.toList());

		// 插入自定义实现
		methodList.add(new SelectById());
		methodList.add(new SelectPage());
		methodList.add(new SelectList());
		methodList.add(new SelectCount());

		// 兼容数据权限：检测 Mapper 是否继承配置的基接口
		if (StringUtils.isNotEmpty(datascopeMapperClass) && isDatascopeMapper(mapperClass)) {
			methodList.add(new SelectListByScope(SqlMethod.SELECT_LIST.getMethod()));
			methodList.add(new SelectPageByScope(SqlMethod.SELECT_PAGE.getMethod()));
		}
		return methodList;
	}

	/**
	 * 检测 Mapper 是否继承数据权限基接口（{@link #datascopeMapperClass}）。
	 * 按全限定名递归遍历接口链支持间接继承；
	 * 不用 ClassUtils.isAssignable 是因该接口由宿主项目提供、gaia 编译期不可见（FQN 匹配为有意设计）。
	 */
	private boolean isDatascopeMapper(Class<?> clazz) {
		for (Class<?> itf : clazz.getInterfaces()) {
			if (datascopeMapperClass.equals(itf.getName()) || isDatascopeMapper(itf)) {
				return true;
			}
		}
		return false;
	}
}


package com.xwang.gaia.core.mybatisplus.method;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.xwang.gaia.core.mybatisplus.annotation.Relation;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author dux.fangl
 * @date 2020/4/26
 */
public class SelectPageByScope extends AbstractEMethod {

	/**
	 * @param methodName 方法名
	 * @since 3.5.0
	 */
	public SelectPageByScope(String methodName) {
		super(methodName);
	}

	@Override
	public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
		SqlMethod sqlMethod = SqlMethod.SELECT_PAGE;

		List<Field> list =
				Stream.of(tableInfo.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(Relation.class)).collect(Collectors.toList());
		String tableName = "";
		String relationField = "";
		if (CollectionUtil.isNotEmpty(list)) {
			for (Field field : list) {
				Relation relation = field.getAnnotation(Relation.class);
				tableName += (" " + relation.condition());
				relationField += ("," + relation.columnStr());
				classColumnMap.computeIfAbsent(mapperClass.getName() + SelectPageByScope.class.getSimpleName(),
						k -> new ConcurrentHashMap<>())
					.put(field.getName(), new AbstractEMethod.FieldRel(field, relation));
			}
		}
		String sqlSelectColumns;
		String sqlWhereEntityWrapper;
		if (StrUtil.isNotBlank(tableName)) {
			tableName = tableInfo.getTableName() + " t " + tableName;
			sqlSelectColumns = sqlSelectColumns2(tableInfo, true, relationField, false);
			sqlWhereEntityWrapper = sqlWhereEntityWrapper2(mapperClass, true, tableInfo);
		} else {
			tableName = tableInfo.getTableName();
			sqlSelectColumns = sqlSelectColumns(tableInfo, true, false);
			sqlWhereEntityWrapper = sqlWhereEntityWrapper(true, tableInfo);
		}

		String sql = String.format(sqlMethod.getSql(), sqlFirst(), sqlSelectColumns, tableName, sqlWhereEntityWrapper
				, sqlOrderBy(tableInfo), sqlComment());
		SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
		return this.addSelectMappedStatementForTable(mapperClass, "selectPageByScope", sqlSource, tableInfo);
	}

	@Override
	protected String getSimpleName() {
		return this.getClass().getSimpleName();
	}
}

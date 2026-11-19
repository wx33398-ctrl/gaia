/*
 * Copyright (c) 2011-2020, baomidou (jobob@qq.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
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
 * 自定义实现表关联
 *
 * @author xwang
 */
public class SelectPage extends AbstractEMethod {

	/**
	 * @since 3.5.0
	 */
	public SelectPage() {
		super(SqlMethod.SELECT_PAGE.getMethod());
	}

	@Override
	public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass,
												 TableInfo tableInfo) {
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
				classColumnMap.computeIfAbsent(mapperClass.getName() + SelectPage.class.getSimpleName(),
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
			tableName = tableInfo.getTableName() + " t";
			sqlSelectColumns = sqlSelectColumns(tableInfo, true, false);
			sqlWhereEntityWrapper = sqlWhereEntityWrapper(true, tableInfo);
		}

		String sql = String.format(sqlMethod.getSql(), sqlFirst(), sqlSelectColumns, tableName, sqlWhereEntityWrapper
				, sqlOrderBy(tableInfo), sqlComment());
		SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
		return this.addSelectMappedStatementForTable(mapperClass, sqlMethod.getMethod(), sqlSource, tableInfo);
	}

	@Override
	protected String getSimpleName() {
		return this.getClass().getSimpleName();
	}
}

/*
 * Copyright (c) 2011-2021, baomidou (jobob@qq.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 自定义实现表关联
 * @author xwang
 */
public class SelectCount extends AbstractEMethod {

	/**
	 * @since 3.5.0s
	 */
	public SelectCount() {
		super(SqlMethod.SELECT_COUNT.getMethod());
	}

	@Override
	public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
		SqlMethod sqlMethod = SqlMethod.SELECT_COUNT;
		List<Field> list =
				Stream.of(tableInfo.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(Relation.class)).collect(Collectors.toList());
		String tableName = "";
		if (CollectionUtil.isNotEmpty(list)) {
			for (Field field : list) {
				Relation relation = field.getAnnotation(Relation.class);
				tableName += (" " + relation.condition());
				if (classColumnMap.get(mapperClass.getName() + SelectCount.class.getSimpleName()) == null) {
					classColumnMap.put(mapperClass.getName() + SelectCount.class.getSimpleName(), new HashMap<>());
				}
				classColumnMap.get(mapperClass.getName() + SelectCount.class.getSimpleName()).put(field.getName(),
							new FieldRel(field, relation));
			}
		}
		String sqlWhereEntityWrapper;
		if (StrUtil.isNotBlank(tableName)) {
			tableName = tableInfo.getTableName() + " t " + tableName;
			sqlWhereEntityWrapper = sqlWhereEntityWrapper2(mapperClass, true, tableInfo);
		} else {
			// 主表统一别名 t：与 SelectPage/SelectList/SelectById 对齐，applySorting 输出的 t. 前缀排序列在
			// count 派生 SQL（含 ${ew.sqlSegment} 的 ORDER BY 段）中必须有别名来源
			tableName = tableInfo.getTableName() + " t";
			sqlWhereEntityWrapper = sqlWhereEntityWrapper(true, tableInfo);
		}

		String sql = String.format(sqlMethod.getSql(), sqlFirst(), sqlCount(), tableName,
				sqlWhereEntityWrapper, sqlComment());
		SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
		return this.addSelectMappedStatementForOther(mapperClass, methodName, sqlSource, Long.class);
	}

	@Override
	protected String getSimpleName() {
		return this.getClass().getSimpleName();
	}
}

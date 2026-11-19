package com.xwang.gaia.core.mybatisplus.method;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.xwang.gaia.core.mybatisplus.annotation.Relation;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.TextSqlNode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 自定义实现表关联
 *
 * @author xwang
 */
public class SelectById extends AbstractEMethod {

	/**
	 * @since 3.5.0
	 */
	public SelectById() {
		super(SqlMethod.SELECT_BY_ID.getMethod());
	}

	@Override
	public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
		SqlMethod sqlMethod = SqlMethod.SELECT_BY_ID;
		List<Field> list =
				Stream.of(tableInfo.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(Relation.class)).collect(Collectors.toList());
		String tableName = "";
		String relationField = "";
		if (CollectionUtil.isNotEmpty(list)) {
			for (Field field : list) {
				Relation relation = field.getAnnotation(Relation.class);
				tableName += (" " + relation.condition());
				relationField += ("," + relation.columnStr());
			}
		}
		String keyColumn;
		String logicDeleteSql;
		String sqlSelectColumns;
		if (tableName != null && tableName.trim().length() > 0) {
			tableName = tableInfo.getTableName() + " t " + tableName;
			keyColumn = T_DOT + tableInfo.getKeyColumn();
			sqlSelectColumns = sqlSelectColumns2(tableInfo, false, relationField, true);
			logicDeleteSql = getLogicDeleteSql2(tableInfo, true, true);
		} else {
			tableName = tableInfo.getTableName() + " t";
			keyColumn = tableInfo.getKeyColumn();
			sqlSelectColumns = sqlSelectColumns(tableInfo, false, true);
			logicDeleteSql = tableInfo.getLogicDeleteSql(true, true);
		}

		SqlSource sqlSource = new DynamicSqlSource(configuration, new TextSqlNode(String.format(sqlMethod.getSql(),
				sqlSelectColumns, tableName, keyColumn, tableInfo.getKeyProperty(), logicDeleteSql)));
		return this.addSelectMappedStatementForTable(mapperClass, sqlMethod.getMethod(), sqlSource, tableInfo);
	}

	@Override
	protected String getSimpleName() {
		return this.getClass().getSimpleName();
	}
}

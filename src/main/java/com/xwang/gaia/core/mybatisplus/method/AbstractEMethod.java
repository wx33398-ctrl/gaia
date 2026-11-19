package com.xwang.gaia.core.mybatisplus.method;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.SqlCondition;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import com.xwang.gaia.core.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public abstract class AbstractEMethod extends AbstractMethod {

	protected static final String T_DOT = "t.";

	protected transient Map<String, Map<String, FieldRel>> classColumnMap = new ConcurrentHashMap<>();

	/**
	 * @param methodName 方法名
	 * @since 3.5.0
	 */
	protected AbstractEMethod(String methodName) {
		super(methodName);
	}

	/**
	 * 首字母小写
	 *
	 * @param str
	 * @return
	 */
	public static String lowerCaseFirst(String str) {
		return StrUtil.isNotEmpty(str) ? str.substring(0, 1).toLowerCase() + str.substring(1) : "";
	}


	/**
	 * SQL 查询所有表字段
	 *
	 * @param table        表信息
	 * @param queryWrapper 是否为使用 queryWrapper 查询
	 * @return sql 脚本
	 */
	protected String sqlSelectColumns(TableInfo table, boolean queryWrapper, boolean aggregate) {
		/* 假设存在用户自定义的 resultMap 映射返回 */
		String selectColumns = ASTERISK;
		if (table.getResultMap() == null || table.isAutoInitResultMap()) {
			/* 未设置 resultMap 或者 resultMap 是自动构建的,视为属于mp的规则范围内 */
			selectColumns = table.getAllSqlSelect();
		}
		//Aggregate聚合属性处理
		if (aggregate) {
			selectColumns += procAggregate(table);
		}

		if (!queryWrapper) {
			return selectColumns;
		}
		return convertChooseEwSelect(selectColumns);
	}

	protected String sqlSelectColumns2(TableInfo table, boolean queryWrapper, String relationColumn, boolean aggergateFlag) {
		/* 假设存在 resultMap 映射返回 */
		String selectColumns = ASTERISK;
		if (table.getResultMap() == null || table.isAutoInitResultMap()) {
			/* 普通查询 */
			selectColumns = getAllSqlSelect(table) + relationColumn;
		}
		//Aggregate聚合属性处理
		if (aggergateFlag) {
			selectColumns += procAggregate(table);
		}

		if (!queryWrapper) {
			return selectColumns;
		}
		return SqlScriptUtils.convertChoose(String.format("%s != null and %s != null", WRAPPER, Q_WRAPPER_SQL_SELECT),
				SqlScriptUtils.unSafeParam(Q_WRAPPER_SQL_SELECT), selectColumns);
	}

	protected String procAggregate(TableInfo tableInfo) {
		List<Field> list = Stream.of(tableInfo.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(Aggregate.class)).collect(Collectors.toList());
		String appSelectColumns = "";
		if (CollectionUtil.isNotEmpty(list)) {
			for (Field field : list) {
				Aggregate aggregate = field.getAnnotation(Aggregate.class);
				appSelectColumns += (",(" + aggregate.value() + ") as " + field.getName());
			}
		}
		return appSelectColumns;
	}

	/**
	 * 获取包含主键及字段的 select sql 片段
	 *
	 * @return sql 片段
	 */
	private String getAllSqlSelect(TableInfo table) {
		String allSqlSelect = chooseSelect(table, TableFieldInfo::isSelect);
		ReflectUtil.setFieldValue(table, "allSqlSelect", allSqlSelect);
		return allSqlSelect;
	}

	/**
	 * 获取需要进行查询的 select sql 片段
	 *
	 * @param predicate 过滤条件
	 * @return sql 片段
	 */
	private String chooseSelect(TableInfo table, Predicate<TableFieldInfo> predicate) {
		String sqlSelect = T_DOT + table.getKeySqlSelect();
		String fieldsSqlSelect = table.getFieldList().stream().filter(predicate)
				.map(s -> T_DOT + s.getSqlSelect()).collect(joining(COMMA));
		if (!StrUtil.equals(sqlSelect, T_DOT) && StrUtil.isNotBlank(fieldsSqlSelect)) {
			return sqlSelect + COMMA + fieldsSqlSelect;
		} else if (StrUtil.isNotBlank(fieldsSqlSelect)) {
			return fieldsSqlSelect;
		}
		return sqlSelect;
	}

	protected String getLogicDeleteSql2(TableInfo tableInfo, boolean startWithAnd, boolean isWhere) {
		if (tableInfo.isWithLogicDelete()) {
			String logicDeleteSql = T_DOT + formatLogicDeleteSql(tableInfo, isWhere);
			if (startWithAnd) {
				logicDeleteSql = " AND " + logicDeleteSql;
			}
			return logicDeleteSql;
		}
		return EMPTY;
	}

	private String formatLogicDeleteSql(TableInfo tableInfo, boolean isWhere) {
		final String value = isWhere ? tableInfo.getLogicDeleteFieldInfo().getLogicNotDeleteValue() :
				tableInfo.getLogicDeleteFieldInfo().getLogicDeleteValue();
		if (isWhere) {
			if (NULL.equalsIgnoreCase(value)) {
				return tableInfo.getLogicDeleteFieldInfo().getColumn() + " IS NULL";
			} else {
				return tableInfo.getLogicDeleteFieldInfo().getColumn() + EQUALS + String.format(tableInfo.getLogicDeleteFieldInfo().isCharSequence() ? "'%s'" : "%s", value);
			}
		}
		final String targetStr = tableInfo.getLogicDeleteFieldInfo().getColumn() + EQUALS;
		if (NULL.equalsIgnoreCase(value)) {
			return targetStr + NULL;
		} else {
			return targetStr + String.format(tableInfo.getLogicDeleteFieldInfo().isCharSequence() ? "'%s'" : "%s",
					value);
		}
	}

	/**
	 * EntityWrapper方式获取select where
	 *
	 * @param newLine 是否提到下一行
	 * @param table   表信息
	 * @return String
	 */
	@Override
	protected String sqlWhereEntityWrapper(boolean newLine, TableInfo table) {
		if (table.isWithLogicDelete()) {
			String sqlScript = table.getAllSqlWhere(false, true, true, WRAPPER_ENTITY_DOT);

			//增加处理时间范围查询字段
			sqlScript = procStartTime(sqlScript, table, "");
			sqlScript = procEndTime(sqlScript, table, "");
			sqlScript = procCompare(sqlScript, table, "");
			sqlScript = procIn(sqlScript, table, "");

			sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER_ENTITY),
					true);
			sqlScript += (NEWLINE + table.getLogicDeleteSql(true, true) + NEWLINE);
			String normalSqlScript = SqlScriptUtils.convertIf(String.format("AND ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_NONEMPTYOFNORMAL), true);
			normalSqlScript += NEWLINE;
			normalSqlScript += SqlScriptUtils.convertIf(String.format(" ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_EMPTYOFNORMAL), true);
			sqlScript += normalSqlScript;
			sqlScript = SqlScriptUtils.convertChoose(String.format("%s != null", WRAPPER), sqlScript,
					table.getLogicDeleteSql(false, true));
			sqlScript = SqlScriptUtils.convertWhere(sqlScript);
			return newLine ? NEWLINE + sqlScript : sqlScript;
		} else {
			String sqlScript = table.getAllSqlWhere(false, false, true, WRAPPER_ENTITY_DOT);

			//增加处理时间范围查询字段
			sqlScript = procStartTime(sqlScript, table, "");
			sqlScript = procEndTime(sqlScript, table, "");
			sqlScript = procCompare(sqlScript, table, "");
			sqlScript = procIn(sqlScript, table, "");

			sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER_ENTITY), true);
			// 与逻辑删除分支对齐：AND 连接符只看 nonEmptyOfNormal（nonEmptyOfEntity 不识别伪字段条件，
			// 曾致「伪字段时间范围+extra 条件」拼出缺 AND 的坏 SQL → count 解析错乱）；前导 AND 由 <where> 自动剥
			sqlScript += SqlScriptUtils.convertIf(String.format("AND ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_NONEMPTYOFNORMAL), true);
			sqlScript = SqlScriptUtils.convertWhere(sqlScript) + NEWLINE;
			// 排序段条件对齐 emptyOfNormal（emptyOfWhere 在实体真实列 eq 时会误为 false → order by 被丢）
			sqlScript += SqlScriptUtils.convertIf(String.format(" ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_EMPTYOFNORMAL), true);
			sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER), true);
			return newLine ? NEWLINE + sqlScript : sqlScript;
		}
	}

	/**
	 * EntityWrapper方式获取select where
	 *
	 * @param newLine 是否提到下一行
	 * @param table   表信息
	 * @return String
	 */
	protected String sqlWhereEntityWrapper2(Class<?> mapperClass, boolean newLine, TableInfo table) {
		if (table.isWithLogicDelete()) {
			String sqlScript = getAllSqlWhere2(mapperClass, table, true, true, WRAPPER_ENTITY_DOT);

			//关联属性查询
			sqlScript = procRelationCond(mapperClass, sqlScript, table);

			sqlScript = procStartTime(sqlScript, table, T_DOT);
			sqlScript = procEndTime(sqlScript, table, T_DOT);
			sqlScript = procCompare(sqlScript, table, T_DOT);
			sqlScript = procIn(sqlScript, table, T_DOT);

			sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER_ENTITY), true);
			sqlScript += (NEWLINE + getLogicDeleteSql2(table, true, true) + NEWLINE);

			String normalSqlScript = SqlScriptUtils.convertIf(String.format("AND ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_NONEMPTYOFNORMAL), true);
			normalSqlScript += NEWLINE;
			normalSqlScript += SqlScriptUtils.convertIf(String.format(" ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT,
							WRAPPER_SQLSEGMENT, WRAPPER_EMPTYOFNORMAL), true);
			sqlScript += normalSqlScript;
			sqlScript = SqlScriptUtils.convertChoose(String.format("%s != null", WRAPPER), sqlScript,
					getLogicDeleteSql2(table, false, true));
			sqlScript = SqlScriptUtils.convertWhere(sqlScript);
			return newLine ? NEWLINE + sqlScript : sqlScript;
		} else {
			String sqlScript = getAllSqlWhere2(mapperClass, table, false, true, WRAPPER_ENTITY_DOT);

			//关联属性查询
			sqlScript = procRelationCond(mapperClass, sqlScript, table);

			//增加处理时间范围查询字段
			sqlScript = procStartTime(sqlScript, table, T_DOT);
			sqlScript = procEndTime(sqlScript, table, T_DOT);
			sqlScript = procCompare(sqlScript, table, T_DOT);
			sqlScript = procIn(sqlScript, table, T_DOT);

			sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER_ENTITY), true);
			// 同 sqlWhereEntityWrapper 非逻辑删除分支：AND 连接符只看 nonEmptyOfNormal，排序段用 emptyOfNormal
			sqlScript += SqlScriptUtils.convertIf(String.format("AND ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_NONEMPTYOFNORMAL), true);
			sqlScript = SqlScriptUtils.convertWhere(sqlScript) + NEWLINE;
			sqlScript += SqlScriptUtils.convertIf(String.format(" ${%s}", WRAPPER_SQLSEGMENT),
					String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
							WRAPPER_EMPTYOFNORMAL), true);
			sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER), true);
			return newLine ? NEWLINE + sqlScript : sqlScript;
		}
	}

	/**
	 * 获取所有的查询的 sql 片段
	 *
	 * @param ignoreLogicDelFiled 是否过滤掉逻辑删除字段
	 * @param withId              是否包含 id 项
	 * @param prefix              前缀
	 * @return sql 脚本片段
	 */
	private String getAllSqlWhere2(Class<?> mapperClass, TableInfo table, boolean ignoreLogicDelFiled, boolean withId,
								   final String prefix) {
		final String newPrefix = prefix == null ? EMPTY : prefix;
		String filedSqlScript = table.getFieldList().stream()
				.filter(i -> {
					if (ignoreLogicDelFiled) {
						return !(table.isWithLogicDelete() && i.isLogicDelete());
					}
					return true;
				})
				.map(i -> getSqlWhere(mapperClass, i, newPrefix)).filter(Objects::nonNull).collect(joining(NEWLINE));
		if (!withId || StringUtils.isBlank(table.getKeyProperty())) {
			return filedSqlScript;
		}
		String newKeyProperty = newPrefix + table.getKeyProperty();
		String keySqlScript = T_DOT + table.getKeyColumn() + EQUALS + SqlScriptUtils.safeParam(newKeyProperty);
		return SqlScriptUtils.convertIf(keySqlScript, String.format("%s != null", newKeyProperty), false)
				+ NEWLINE + filedSqlScript;
	}

	private String getSqlWhere(Class<?> mapperClass, TableFieldInfo field, final String prefix) {
		final String newPrefix = prefix == null ? EMPTY : prefix;
		// 默认:  AND column=#{prefix + el}
		String columnPre = "";
		String key = mapperClass.getName() + getSimpleName();
		if (classColumnMap.get(key) != null && classColumnMap.get(key).get(field.getProperty()) != null) {
			columnPre = classColumnMap.get(key).get(field.getProperty()).getRelation().wherePrefix();
		}
		String sqlScript = "";
		if (StrUtil.isNotEmpty(columnPre)) {
			sqlScript = " AND " + String.format(SqlCondition.LIKE, columnPre + DOT + field.getProperty(),
					newPrefix + field.getEl());
		} else {
			sqlScript = " AND " + String.format(field.getCondition(), T_DOT + field.getColumn(),
					newPrefix + field.getEl());
		}
		// 查询的时候只判非空
		return convertIf(field, sqlScript, newPrefix + field.getProperty(), field.getWhereStrategy());
	}

	private String convertIf(TableFieldInfo field, final String sqlScript, final String property,
							 final FieldStrategy fieldStrategy) {
		if (fieldStrategy == FieldStrategy.NEVER) {
			return null;
		}
	
		if (fieldStrategy == FieldStrategy.NOT_EMPTY && field.isCharSequence()) {
			return SqlScriptUtils.convertIf(sqlScript, String.format("%s != null and %s != ''", property, property),
					false);
		}
		return SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", property), false);
	}

	/**
	 * 处理关联关系字段作为查询条件的情况
	 * 字段类型、原始字段、一对多或多对一、
	 *
	 * @param sqlScript
	 * @return
	 */
	private String procRelationCond(Class<?> mapperClass, String sqlScript, TableInfo table) {
		StringBuilder sqlStringBuilder = new StringBuilder(sqlScript);
		Map<String, FieldRel> map = classColumnMap.get(mapperClass.getName() + getSimpleName());
		if (CollectionUtil.isNotEmpty(map)) {
			Set<Map.Entry<String, FieldRel>> set = map.entrySet();
			StringBuilder sb = new StringBuilder();
			Map<String, String> columnMap;
			for (Map.Entry<String, FieldRel> entry : set) {
				String fieldName = entry.getKey();
				FieldRel fieldRel = entry.getValue();
				if (fieldRel.relation.type() == 0) {
					columnMap = processFields(fieldRel.relation.columnStr());
					Field field;
					for (Map.Entry<String, String> columnEntry : columnMap.entrySet()) {
						field = getField(table, columnEntry.getValue());
						if (field == null) {
							continue;
						}
						String condStr = field.getAnnotation(TableField.class).condition();

						//如果存在属性则增加where条件
						sb.setLength(0);
						sqlStringBuilder.append(NEWLINE);
						sb.append(" AND ");
						if (StrUtil.isEmpty(condStr)) {
							sb.append(fieldRel.relation.wherePrefix()).append(DOT).append(columnEntry.getKey()).append("=").append("#{ew.entity.").append(field.getName()).append("}");
						} else {
							sb.append(String.format(condStr, fieldRel.relation.wherePrefix() + DOT + columnEntry.getKey(), "ew.entity." + field.getName()));
						}

						String fieldNameStr = "ew.entity['" + field.getName() + "']";
						if (field.getType() == String.class) {
							sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(),
									String.format("%s != null and %s != " + "''", fieldNameStr, fieldNameStr), false));
						} else {
							sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null",
									fieldNameStr), false));
						}
					}
				}
			}
		}
		return sqlStringBuilder.toString();
	}

	/**
	 * 根据下划线name找驼峰name
	 *
	 * @param table
	 * @param underlineName
	 * @return
	 */
	private Field getField(TableInfo table, String underlineName) {
		String camelCaseName = StrUtil.toCamelCase(lowerCaseFirst(underlineName));
		try {
			return table.getEntityType().getDeclaredField(camelCaseName);
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	private Map<String, String> processFields(String columnStr) {
		String[] columnArray = columnStr.split(",");
		Map<String, String> map = new HashMap<>();
		for (String item : columnArray) {
			if (StrUtil.isBlank(item)) {
				continue;
			}
			item = item.trim();
			item = item.replaceAll(" +", " ");
			item = item.substring(item.indexOf(".") + 1);
			String[] columnItem = item.split(" ");
			if (columnItem.length == 1) {
				map.put(columnItem[0], columnItem[0]);
			} else {
				map.put(columnItem[0], columnItem[1]);
			}
		}
		return map;
	}


	/**
	 * 处理@StartTime注解的范围条件
	 *
	 * @param sqlScript
	 * @param table
	 * @param prefix
	 * @return
	 */
	private String procStartTime(String sqlScript, TableInfo table, String prefix) {
		StringBuilder sqlStringBuilder = new StringBuilder(sqlScript);
		List<Field> list =
				Stream.of(table.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(StartTime.class)).collect(Collectors.toList());
		if (CollectionUtil.isNotEmpty(list)) {
			StringBuilder sb = new StringBuilder();
			for (Field field : list) {
				sb.setLength(0);
				StartTime startTime = field.getAnnotation(StartTime.class);
				//增加时间范围查询字段
				sqlStringBuilder.append(NEWLINE);
					sb.append(" AND ").append(prefix).append(startTime.column()).append(" &gt;= ").append("#{ew" +
							".entity" +
							".").append(field.getName()).append("}");
				String fieldName = "ew.entity['" + field.getName() + "']";
                if (StrUtil.equalsIgnoreCase("String", startTime.type())) {
                    sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null and %s != ''",
                            fieldName, fieldName), false));
                } else {
                    sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null",
                            fieldName, fieldName), false));
                }
			}
		}
		return sqlStringBuilder.toString();
	}

	/**
	 * 比较大小判断
	 *
	 * @param sqlScript
	 * @param table
	 * @param prefix
	 * @return
	 */
	private String procIn(String sqlScript, TableInfo table, String prefix) {
		StringBuilder sqlStringBuilder = new StringBuilder(sqlScript);
		List<Field> list =
				Stream.of(table.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(In.class)).collect(Collectors.toList());
		if (CollectionUtil.isNotEmpty(list)) {
			StringBuilder sb = new StringBuilder();
			for (Field field : list) {
				sb.setLength(0);
				In in = field.getAnnotation(In.class);
				String operator = " IN ";
				if (in.type() == 1) {
					operator = " NOT IN ";
				}
				//增加时间范围查询字段
				sqlStringBuilder.append(NEWLINE);
				sb.append(" AND ").append(prefix).append(StrUtil.toSymbolCase(in.column(), '_')).append(operator);
				sb.append("<foreach collection=\"ew.entity['").append(field.getName()).append("']\"");
				sb.append(" item=\"item\" open=\"(\" close=\")\" separator=\",\">#{item}</foreach>");
				String fieldName = "ew.entity['" + field.getName() + "']";
				sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null and %s.size() > 0",
						fieldName, fieldName), false));
			}
		}
		return sqlStringBuilder.toString();
	}

	/**
	 * 比较大小判断
	 *
	 * @param sqlScript
	 * @param table
	 * @param prefix
	 * @return
	 */
	private String procCompare(String sqlScript, TableInfo table, String prefix) {
		StringBuilder sqlStringBuilder = new StringBuilder(sqlScript);
		List<Field> list =
				Stream.of(table.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(Compare.class)).collect(Collectors.toList());
		if (CollectionUtil.isNotEmpty(list)) {
			StringBuilder sb = new StringBuilder();
			for (Field field : list) {
				sb.setLength(0);
				Compare compare = field.getAnnotation(Compare.class);
				//增加时间范围查询字段
				sqlStringBuilder.append(NEWLINE);
				//如果字段已经包含t3.column前缀，则不拼接't.'前缀
				String column = compare.column().contains(".")
						? StrUtil.toSymbolCase(compare.column(), '_')
						: prefix + StrUtil.toSymbolCase(compare.column(), '_');
				String param = "#{ew.entity." + field.getName() + "}";
				String operate = compare.operate();
				sb.append(" AND ").append(column).append(" ");
				// like 系列自动用 CONCAT 包 %：leftLike 前%、rightLike 后%、like 左右%；其余 operate 原样拼接
				if ("leftLike".equalsIgnoreCase(operate)) {
					sb.append("LIKE CONCAT('%',").append(param).append(")");
				} else if ("rightLike".equalsIgnoreCase(operate)) {
					sb.append("LIKE CONCAT(").append(param).append(",'%')");
				} else if ("like".equalsIgnoreCase(operate)) {
					sb.append("LIKE CONCAT('%',").append(param).append(",'%')");
				} else {
					sb.append(operate).append(param);
				}

				String fieldName = "ew.entity['" + field.getName() + "']";
				if(field.getType() == String.class) {
					sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null and %s != ''",
							fieldName, fieldName), false));
				} else {
					sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null",
							fieldName, fieldName), false));
				}
			}
		}
		return sqlStringBuilder.toString();
	}


	/**
	 * 处理@endTime注解的范围条件
	 *
	 * @param sqlScript
	 * @param table
	 * @param prefix
	 * @return
	 */
	private String procEndTime(String sqlScript, TableInfo table, String prefix) {
		StringBuilder sqlStringBuilder = new StringBuilder(sqlScript);
		List<Field> list =
				Stream.of(table.getEntityType().getDeclaredFields()).filter(s -> s.isAnnotationPresent(EndTime.class)).collect(Collectors.toList());
		if (CollectionUtil.isNotEmpty(list)) {
			StringBuilder sb = new StringBuilder();
			for (Field field : list) {
				sb.setLength(0);
				EndTime endTime = field.getAnnotation(EndTime.class);
				//增加时间范围查询字段
				sqlStringBuilder.append(NEWLINE);
                sb.append(" AND ").append(prefix).append(endTime.column()).append(" &lt;= ").append("#{ew.entity.").append(field.getName()).append("}");
				String fieldName = "ew.entity['" + field.getName() + "']";
                if (StrUtil.equalsIgnoreCase("String", endTime.type())) {
                    sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null and %s != ''",
                            fieldName, fieldName), false));
                } else {
                    sqlStringBuilder.append(SqlScriptUtils.convertIf(sb.toString(), String.format("%s != null",
                            fieldName, fieldName), false));
                }
			}
		}
		return sqlStringBuilder.toString();
	}

	protected abstract String getSimpleName();

	@Data
	@AllArgsConstructor
	static class FieldRel {
		private Field field;
		private Relation relation;
	}
}

package com.xwang.gaia.core.mybatisplus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.SqlCondition;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xwang.gaia.core.mybatisplus.annotation.Aggregate;
import com.xwang.gaia.core.mybatisplus.annotation.Compare;
import com.xwang.gaia.core.mybatisplus.annotation.EndTime;
import com.xwang.gaia.core.mybatisplus.annotation.In;
import com.xwang.gaia.core.mybatisplus.annotation.Relation;
import com.xwang.gaia.core.mybatisplus.annotation.StartTime;
import com.xwang.gaia.testfix.ScopeBaseMapper;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * golden-SQL 基线测试的实体/ Mapper fixture。
 * 覆盖矩阵：@Relation 联表（有/无）× 逻辑删除（开/关）× @Aggregate/@In/@Compare/@StartTime/@EndTime。
 * 注解载体字段一律 @TableField(exist=false)（与业务实际用法一致：不进 fieldList，
 * 只由各 proc* 增强器读取生成 WHERE 片段）。
 * columnStr 语法为当前 processFields 实际支持的 "别名.列 实体属性名"（无 as）。
 */
public class GoldenFixture {

	/** 联表 + 逻辑删除实体 */
	@Data
	@TableName("golden_order")
	public static class GoldenOrder {

		@TableId(type = IdType.AUTO)
		private Integer id;

		private String name;

		private Integer status;

		private BigDecimal amount;

		private LocalDateTime createTime;

		private Integer deptId;

		/** 逻辑删除：String 型 → 逻辑未删值带引号 '0' */
		@TableLogic
		private String delFlag;

		/** 时间范围查询载体（>=） */
		@StartTime(column = "create_time", type = "datetime")
		@TableField(exist = false)
		private LocalDateTime startTime;

		/** 时间范围查询载体（<=） */
		@EndTime(column = "create_time", type = "datetime")
		@TableField(exist = false)
		private LocalDateTime endTime;

		/** 比较载体：普通列名（拼 t. 前缀分支） */
		@Compare(column = "amount", operate = ">=")
		@TableField(exist = false)
		private BigDecimal minAmount;

		/** IN 载体（type=0） */
		@In(column = "status")
		@TableField(exist = false)
		private List<Integer> statusList;

		/** NOT IN 载体（type=1） */
		@In(column = "id", type = 1)
		@TableField(exist = false)
		private List<Integer> excludeIds;

		/** 聚合子查询（selectById 路径生效） */
		@Aggregate("select count(1) from golden_order o where o.dept_id = t.dept_id")
		@TableField(exist = false)
		private Integer orderCount;

		/** 联表字段：默认等值条件 */
		@Relation(condition = "left join golden_dept d on d.id = t.dept_id",
				columnStr = "d.dept_name deptName", wherePrefix = "d")
		@TableField(exist = false)
		private String deptName;

		/** 联表字段：映射字段标了 @TableField(condition=LIKE)，走 format 分支 */
		@Relation(condition = "left join golden_dept g on g.id = t.dept_id",
				columnStr = "g.phone phone", wherePrefix = "g")
		@TableField(exist = false, condition = SqlCondition.LIKE)
		private String phone;
	}

	/** 无联表 + 无逻辑删除实体 */
	@Data
	@TableName("golden_user")
	public static class GoldenUser {

		@TableId(type = IdType.AUTO)
		private Long id;

		private String name;

		private Integer age;

		private LocalDateTime createTime;

		@StartTime(column = "create_time", type = "datetime")
		@TableField(exist = false)
		private LocalDateTime startTime;

		@EndTime(column = "create_time", type = "datetime")
		@TableField(exist = false)
		private LocalDateTime endTime;

		/** 比较载体：column 已含表别名前缀（不拼 t. 分支） */
		@Compare(column = "age", operate = ">=")
		@TableField(exist = false)
		private Integer minAge;

		/** 比较载体：column 带 "t." 前缀 → contains(".") 分支（业务实况：'<'/<=' 会破坏 XML 脚本，均不可用） */
		@Compare(column = "t.age", operate = ">=")
		@TableField(exist = false)
		private Integer maxAge;

		@In(column = "age")
		@TableField(exist = false)
		private List<Integer> ageList;

		@Aggregate("select count(1) from golden_order o where o.dept_id = golden_user.id")
		@TableField(exist = false)
		private Integer orderCount;
	}

	/** 联表实体 Mapper：实现数据权限替身 ScopeBaseMapper → 应额外注入 ByScope 两语句 */
	public interface GoldenOrderMapper extends ScopeBaseMapper<GoldenOrder> {
	}

	/** 普通实体 Mapper → 仅 4 个 Gaia 替换语句 */
	public interface GoldenUserMapper extends BaseMapper<GoldenUser> {
	}
}

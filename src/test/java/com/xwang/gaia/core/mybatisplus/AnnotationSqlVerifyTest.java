package com.xwang.gaia.core.mybatisplus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.SqlCondition;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.xwang.gaia.core.mybatisplus.annotation.Aggregate;
import com.xwang.gaia.core.mybatisplus.annotation.Compare;
import com.xwang.gaia.core.mybatisplus.annotation.EndTime;
import com.xwang.gaia.core.mybatisplus.annotation.In;
import com.xwang.gaia.core.mybatisplus.annotation.Relation;
import com.xwang.gaia.core.mybatisplus.annotation.StartTime;
import com.xwang.gaia.core.mybatisplus.injector.MySqlInjector;
import lombok.Data;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注解 SQL 行为核验（断言版）：逐个注解单独生效，用框架真实「注入 + 渲染」机制生成 SQL，
 * 断言 skill 文档对每个注解的描述与实测一致。任一行为漂移即变红。
 */
class AnnotationSqlVerifyTest {

	@Data @TableName("v_compare")
	public static class CmpEntity {
		@TableId(type = IdType.AUTO) private Long id;
		@Compare(column = "amount", operate = ">=") @TableField(exist = false) private BigDecimal geAmount;
		@Compare(column = "age", operate = "&lt;") @TableField(exist = false) private Integer ltAge;
		@Compare(column = "t.age", operate = ">=") @TableField(exist = false) private Integer dotAge;
		@Compare(column = "name", operate = "like") @TableField(exist = false) private String nameLike;
		@Compare(column = "name", operate = "leftLike") @TableField(exist = false) private String nameLeftLike;
		@Compare(column = "name", operate = "rightLike") @TableField(exist = false) private String nameRightLike;
	}
	public interface CmpMapper extends BaseMapper<CmpEntity> {}

	@Data @TableName("v_in")
	public static class InEntity {
		@TableId(type = IdType.AUTO) private Long id;
		@In(column = "status") @TableField(exist = false) private List<Integer> statusList;
		@In(column = "id", type = 1) @TableField(exist = false) private List<Long> excludeIds;
	}
	public interface InMapper extends BaseMapper<InEntity> {}

	@Data @TableName("v_time")
	public static class TimeEntity {
		@TableId(type = IdType.AUTO) private Long id;
		@StartTime(column = "create_time", type = "datetime") @TableField(exist = false) private LocalDateTime startDt;
		@EndTime(column = "create_time", type = "datetime") @TableField(exist = false) private LocalDateTime endDt;
		@StartTime(column = "biz_date", type = "String") @TableField(exist = false) private String startStr;
	}
	public interface TimeMapper extends BaseMapper<TimeEntity> {}

	@Data @TableName("v_rel")
	public static class RelEntity {
		@TableId(type = IdType.AUTO) private Long id;
		private Long deptId;
		@Relation(condition = "left join dept d on d.id = t.dept_id",
				columnStr = "d.dept_name deptName", wherePrefix = "d")
		@TableField(exist = false) private String deptName;
	}
	public interface RelMapper extends BaseMapper<RelEntity> {}

	@Data @TableName("v_rel_noprefix")
	public static class RelNoPrefixEntity {
		@TableId(type = IdType.AUTO) private Long id;
		private Long deptId;
		@Relation(condition = "left join dept d on d.id = t.dept_id",
				columnStr = "d.dept_name deptName")   // 故意不写 wherePrefix
		@TableField(exist = false) private String deptName;
	}
	public interface RelNoPrefixMapper extends BaseMapper<RelNoPrefixEntity> {}

	@Data @TableName("v_rel_like")
	public static class RelLikeEntity {
		@TableId(type = IdType.AUTO) private Long id;
		private Long deptId;
		@Relation(condition = "left join dept g on g.id = t.dept_id",
				columnStr = "g.phone phone", wherePrefix = "g")
		@TableField(exist = false, condition = SqlCondition.LIKE) private String phone;
	}
	public interface RelLikeMapper extends BaseMapper<RelLikeEntity> {}

	@Data @TableName("v_agg")
	public static class AggEntity {
		@TableId(type = IdType.AUTO) private Long id;
		private Long deptId;
		@Aggregate("select count(1) from item i where i.goods_id = t.id") @TableField(exist = false) private Integer cnt;
	}
	public interface AggMapper extends BaseMapper<AggEntity> {}

	@Data @TableName("v_agg_rel")
	public static class AggRelEntity {
		@TableId(type = IdType.AUTO) private Long id;
		private Long deptId;
		@Aggregate("select count(1) from item i where i.goods_id = t.id") @TableField(exist = false) private Integer cnt;
		@Relation(condition = "left join dept d on d.id = t.dept_id",
				columnStr = "d.dept_name deptName", wherePrefix = "d")
		@TableField(exist = false) private String deptName;
	}
	public interface AggRelMapper extends BaseMapper<AggRelEntity> {}

	// ===== 渲染（同 GoldenSqlTest） ===== //

	private static String render(Class<?> mapperClass, Class<?> entityClass, String method, Object entity) {
		MybatisConfiguration configuration = new MybatisConfiguration();
		GlobalConfig globalConfig = GlobalConfigUtils.defaults();
		globalConfig.setBanner(false);
		GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
		assistant.setCurrentNamespace(mapperClass.getName());
		TableInfo table = TableInfoHelper.initTableInfo(assistant, entityClass);
		MySqlInjector injector = new MySqlInjector();
		for (AbstractMethod method1 : injector.getMethodList(mapperClass, table)) {
			method1.inject(assistant, mapperClass, entityClass, table);
		}
		MappedStatement ms = configuration.getMappedStatement(mapperClass.getName() + "." + method, false);
		Map<String, Object> p = new HashMap<>();
		if (method.equals("selectById")) {
			p.put("id", 1);
		} else {
			QueryWrapper<Object> w = new QueryWrapper<>();
			if (entity != null) w.setEntity(entity);
			p.put("ew", w);
			p.put("et", entity);
		}
		try {
			Field f = DynamicSqlSource.class.getDeclaredField("rootSqlNode");
			f.setAccessible(true);
			SqlNode node = (SqlNode) f.get(ms.getSqlSource());
			DynamicContext ctx = new DynamicContext(configuration, p);
			node.apply(ctx);
			return ctx.getSql().replaceAll("\\s+", " ").trim();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(method + " 非 DynamicSqlSource", e);
		}
	}

	@Test
	void Compare_单表裸列名_小于号渲染_lt_不重复前缀() {
		CmpEntity c = new CmpEntity(); c.setGeAmount(new BigDecimal("9"));
		String ge = render(CmpMapper.class, CmpEntity.class, "selectList", c);
		assertTrue(ge.contains("amount >=#{ew.entity.geAmount}"), "@Compare >= 应拼裸列名: " + ge);
		assertFalse(ge.contains("t.amount"), "单表不应加 t. 前缀: " + ge);

		c = new CmpEntity(); c.setLtAge(5);
		assertTrue(render(CmpMapper.class, CmpEntity.class, "selectList", c).contains("age <#{ew.entity.ltAge}"),
				"&lt; 应渲染成 <");

		c = new CmpEntity(); c.setDotAge(5);
		String dot = render(CmpMapper.class, CmpEntity.class, "selectList", c);
		assertTrue(dot.contains("t.age >=#{ew.entity.dotAge}"), "column 含 . 原样用: " + dot);
		assertFalse(dot.contains("t.t.age"), "不应重复 t. 前缀: " + dot);
	}

	@Test
	void In_正反向() {
		InEntity in = new InEntity(); in.setStatusList(List.of(1, 2));
		assertTrue(render(InMapper.class, InEntity.class, "selectList", in).contains("status IN ("), "@In type=0");
		in = new InEntity(); in.setExcludeIds(List.of(9L));
		assertTrue(render(InMapper.class, InEntity.class, "selectList", in).contains("id NOT IN ("), "@In type=1 NOT IN");
	}

	@Test
	void 时间范围_与_String空串不拼() {
		TimeEntity t = new TimeEntity(); t.setStartDt(LocalDateTime.now()); t.setEndDt(LocalDateTime.now());
		String s = render(TimeMapper.class, TimeEntity.class, "selectList", t);
		assertTrue(s.contains("create_time >= ") && s.contains("create_time <= "), "datetime 范围: " + s);

		t = new TimeEntity(); t.setStartStr("");
		assertFalse(render(TimeMapper.class, TimeEntity.class, "selectList", t).contains("biz_date"),
				"String 空串应判 != '' 不拼");
		t = new TimeEntity(); t.setStartStr("2026-01-01");
		assertTrue(render(TimeMapper.class, TimeEntity.class, "selectList", t).contains("biz_date >= "), "String 传值拼 >=");
	}

	@Test
	void Relation_wherePrefix_与_LIKE_与_缺前缀坏SQL() {
		RelEntity r = new RelEntity(); r.setDeptName("研发");
		String s = render(RelMapper.class, RelEntity.class, "selectPage", r);
		assertTrue(s.contains("d.dept_name deptName"), "SELECT 含联表列: " + s);
		assertTrue(s.contains("WHERE d.dept_name=#{ew.entity.deptName}"), "WHERE 用 wherePrefix: " + s);

		RelNoPrefixEntity rn = new RelNoPrefixEntity(); rn.setDeptName("研发");
		assertTrue(render(RelNoPrefixMapper.class, RelNoPrefixEntity.class, "selectPage", rn).contains("WHERE .dept_name="),
				"无 wherePrefix 应暴露 .dept_name 坏 SQL（证明 wherePrefix 必填）");

		RelLikeEntity rl = new RelLikeEntity(); rl.setPhone("138");
		assertTrue(render(RelLikeMapper.class, RelLikeEntity.class, "selectPage", rl)
				.contains("g.phone LIKE CONCAT('%',#{ew.entity.phone},'%')"), "字段标 LIKE 走 LIKE CONCAT");
	}

	@Test
	void Aggregate_仅_selectById_生效_列表与分页均不拼() {
		AggEntity a = new AggEntity();
		assertTrue(render(AggMapper.class, AggEntity.class, "selectById", null).contains("as cnt"), "selectById 有聚合列");
		assertFalse(render(AggMapper.class, AggEntity.class, "selectPage", a).contains("as cnt"), "selectPage 无聚合列");
		// 关联子查询按结果行执行，N 行 = N 次子查询；列表场景代价不可控，故与 selectPage 一致不拼
		assertFalse(render(AggMapper.class, AggEntity.class, "selectList", a).contains("as cnt"), "无联表 selectList 无聚合列");

		AggRelEntity ar = new AggRelEntity();
		assertFalse(render(AggRelMapper.class, AggRelEntity.class, "selectList", ar).contains("as cnt"), "联表 selectList 无聚合列");
		assertTrue(render(AggRelMapper.class, AggRelEntity.class, "selectById", null).contains("as cnt"), "联表 selectById 有聚合列");
	}

	@Test
	void Compare_like系列自动包百分号() {
		CmpEntity c = new CmpEntity(); c.setNameLike("abc");
		assertTrue(render(CmpMapper.class, CmpEntity.class, "selectList", c)
				.contains("name LIKE CONCAT('%',#{ew.entity.nameLike},'%')"), "like 自动包左右%");

		c = new CmpEntity(); c.setNameLeftLike("abc");
		assertTrue(render(CmpMapper.class, CmpEntity.class, "selectList", c)
				.contains("name LIKE CONCAT('%',#{ew.entity.nameLeftLike})"), "leftLike 自动包前%");

		c = new CmpEntity(); c.setNameRightLike("abc");
		assertTrue(render(CmpMapper.class, CmpEntity.class, "selectList", c)
				.contains("name LIKE CONCAT(#{ew.entity.nameRightLike},'%')"), "rightLike 自动包后%");
	}
}

package com.xwang.gaia.core.mybatisplus;

import com.xwang.gaia.testfix.ScopeBaseMapper;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.xwang.gaia.core.mybatisplus.injector.MySqlInjector;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * golden-SQL 基线测试：SQL 生成行为的逐字锁定。
 * 基线从源库原样复制（一字节不改），任何 SQL 漂移都会使本测试变红。
 * 渲染方式：取 DynamicSqlSource.rootSqlNode 应用到 DynamicContext，
 * 保留 #{} 占位符与 <if>/<foreach> 求值结果（比 BoundSql 的 ? 更具诊断性）。
 * 基线存放：test 资源 golden/golden-sql.txt（=== 分隔符格式，避免文本块空白事故）。
 * 漂移确认后重新录制：运行 录制基线() 重写 target/golden-dump.txt，人工核对 diff 后覆盖资源文件。
 */
class GoldenSqlTest {

	private static MybatisConfiguration configuration;

	@BeforeAll
	static void injectStatements() {
		configuration = new MybatisConfiguration();
		GlobalConfig globalConfig = GlobalConfigUtils.defaults();
		globalConfig.setBanner(false);
		GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
		// 不走 inspectInject（其泛型提取依赖 Mapper 标记接口解析），
		// 显式指定实体类逐方法注入 —— 与生产路径提取后交给注入器的入参等价
		injectMapper(GoldenFixture.GoldenOrderMapper.class, GoldenFixture.GoldenOrder.class);
		injectMapper(GoldenFixture.GoldenUserMapper.class, GoldenFixture.GoldenUser.class);
	}

	/** 对单个 Mapper 注入全部语句（含 Gaia 替换与 ByScope 变体） */
	private static void injectMapper(Class<?> mapperClass, Class<?> entityClass) {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
		assistant.setCurrentNamespace(mapperClass.getName());
		TableInfo table = TableInfoHelper.initTableInfo(assistant, entityClass);
		MySqlInjector injector = new MySqlInjector(ScopeBaseMapper.class.getName());
		for (AbstractMethod method : injector.getMethodList(mapperClass, table)) {
			method.inject(assistant, mapperClass, entityClass, table);
		}
	}

	@Test
	void 联表Mapper六语句与普通Mapper四语句均已注入() {
		for (String id : List.of("selectById", "selectPage", "selectList", "selectCount",
				"selectListByScope", "selectPageByScope")) {
			assertNotNull(statement(GoldenFixture.GoldenOrderMapper.class, id), id);
		}
		for (String id : List.of("selectById", "selectPage", "selectList", "selectCount")) {
			assertNotNull(statement(GoldenFixture.GoldenUserMapper.class, id), id);
		}
	}

	/** 基线门禁：当前 SQL 生成结果必须与录制资源逐字一致（移植安全网） */
	@Test
	void 基线与录制资源逐字一致() {
		Map<String, String> actual = renderMatrix();
		Map<String, String> recorded = loadRecorded();
		assertEquals(recorded.keySet(), actual.keySet(), "基线条目集不一致");
		for (Map.Entry<String, String> e : recorded.entrySet()) {
			assertEquals(e.getValue(), actual.get(e.getKey()), "SQL 漂移: " + e.getKey());
		}
	}

	/** 重新录制工具（非断言）：写 target/golden-dump.txt，人工核对后覆盖资源 */
	@Test
	void 录制基线() throws Exception {
		StringBuilder dump = new StringBuilder();
		for (Map.Entry<String, String> e : renderMatrix().entrySet()) {
			dump.append("<<<").append(e.getKey()).append(">>>\n").append(e.getValue()).append('\n');
		}
		Path out = Path.of("target", "golden-dump.txt");
		Files.writeString(out, dump.toString(), StandardCharsets.UTF_8);
		System.out.println("golden dump written: " + out.toAbsolutePath());
	}

	/** 渲染全部语句 × 全部条件形态（顺序稳定，作为录制/比对唯一入口） */
	private static Map<String, String> renderMatrix() {
		Map<String, String> matrix = new LinkedHashMap<>();
		record(matrix, GoldenFixture.GoldenOrderMapper.class, GoldenFixture.GoldenOrder.class, "selectById");
		for (String id : List.of("selectPage", "selectList", "selectCount",
				"selectListByScope", "selectPageByScope")) {
			record(matrix, GoldenFixture.GoldenOrderMapper.class, GoldenFixture.GoldenOrder.class, id);
		}
		record(matrix, GoldenFixture.GoldenUserMapper.class, GoldenFixture.GoldenUser.class, "selectById");
		for (String id : List.of("selectPage", "selectList", "selectCount")) {
			record(matrix, GoldenFixture.GoldenUserMapper.class, GoldenFixture.GoldenUser.class, id);
		}
		return matrix;
	}

	/** 单语句渲染全部条件形态；selectById 无 wrapper，其余三形态（ew 空/空壳/带条件） */
	private static void record(Map<String, String> matrix, Class<?> mapper, Class<?> entity, String method) {
		MappedStatement ms = statement(mapper, method);
		String key = mapper.getSimpleName() + "." + method;
		if (method.equals("selectById")) {
			matrix.put(key + " | id", render(ms, Map.of("id", 1)));
			return;
		}
		matrix.put(key + " | ewNull", render(ms, params(null, null)));
		matrix.put(key + " | ewEmpty", render(ms, params(new QueryWrapper<>(), null)));
		Object e = fullEntity(entity);
		QueryWrapper<Object> w = new QueryWrapper<>();
		w.setEntity(e);
		w.eq("name", "abc");
		matrix.put(key + " | full", render(ms, params(w, e)));
	}

	private static Object fullEntity(Class<?> entity) {
		if (entity == GoldenFixture.GoldenOrder.class) {
			GoldenFixture.GoldenOrder e = new GoldenFixture.GoldenOrder();
			e.setId(1);
			e.setName("n");
			e.setStatus(2);
			e.setDeptId(3);
			e.setCreateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
			e.setAmount(new BigDecimal("9.90"));
			e.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
			e.setEndTime(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
			e.setMinAmount(new BigDecimal("1"));
			e.setStatusList(List.of(1, 2));
			e.setExcludeIds(List.of(9));
			e.setDeptName("x");
			e.setPhone("p");
			return e;
		}
		GoldenFixture.GoldenUser e = new GoldenFixture.GoldenUser();
		e.setId(1L);
		e.setName("n");
		e.setAge(20);
		e.setCreateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
		e.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
		e.setEndTime(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
		e.setMinAge(18);
		e.setMaxAge(60);
		e.setAgeList(List.of(20, 30));
		return e;
	}

	private static Map<String, Object> params(QueryWrapper<?> ew, Object et) {
		Map<String, Object> p = new HashMap<>();
		p.put("ew", ew);
		p.put("et", et);
		return p;
	}

	private static MappedStatement statement(Class<?> mapper, String method) {
		return configuration.getMappedStatement(mapper.getName() + "." + method, false);
	}

	/** 应用 rootSqlNode 渲染脚本：保留 #{} 占位符，动态节点按参数求值 */
	private static String render(MappedStatement ms, Map<String, Object> params) {
		SqlSource src = ms.getSqlSource();
		try {
			Field f = DynamicSqlSource.class
					.getDeclaredField("rootSqlNode");
			f.setAccessible(true);
			var node = (SqlNode) f.get(src);
			var ctx = new DynamicContext(configuration, params);
			node.apply(ctx);
			return ctx.getSql();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("非 DynamicSqlSource，无法渲染: " + src.getClass(), e);
		}
	}

	/** 解析基线资源：<<<key>>> 行后跟 SQL，直至下一标记或 EOF */
	private static Map<String, String> loadRecorded() {
		Map<String, String> map = new LinkedHashMap<>();
		try (InputStream in = GoldenSqlTest.class.getResourceAsStream("/golden/golden-sql.txt")) {
			assertNotNull(in, "缺少基线资源 golden/golden-sql.txt");
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			String[] parts = text.split("(?m)^<<<|>>>");
			// split 后成对：[空, key, sql, key, sql...]
			for (int i = 1; i + 1 < parts.length; i += 2) {
				String key = parts[i].trim();
				// 去掉 SQL 前的单个换行、末尾录制时追加的换行
				String sql = parts[i + 1].replaceAll("^\n", "").replaceAll("\n$", "");
				map.put(key, sql);
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return map;
	}
}

package com.xwang.gaia.core.mybatisplus;

import com.xwang.gaia.testfix.ScopeBaseMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.xwang.gaia.core.mybatisplus.injector.MySqlInjector;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注入器参数化测试：数据权限 Mapper 全限定名由构造参数传入。
 * 空/null=不注入 ByScope；传入 FQN 且 Mapper 接口链命中才注入 ByScope 两方法。
 * 这是源库硬编码 VestaBaseMapper 的配置化替代。
 */
class MySqlInjectorScopeTest {

	private List<String> methodNamesOf(MySqlInjector injector, Class<?> mapperClass, Class<?> entityClass) {
		MybatisConfiguration configuration = new MybatisConfiguration();
		GlobalConfig globalConfig = GlobalConfigUtils.defaults();
		globalConfig.setBanner(false);
		GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
		assistant.setCurrentNamespace(mapperClass.getName());
		TableInfo table = TableInfoHelper.initTableInfo(assistant, entityClass);
		return injector.getMethodList(mapperClass, table).stream()
				.map(m -> m.getClass().getSimpleName()).toList();
	}

	private static boolean hasScope(List<String> names) {
		return names.contains("SelectListByScope") && names.contains("SelectPageByScope");
	}

	@Test
	void 空构造不注入ByScope() {
		List<String> names = methodNamesOf(new MySqlInjector(), GoldenFixture.GoldenOrderMapper.class, GoldenFixture.GoldenOrder.class);
		assertFalse(hasScope(names), "未配置数据权限 Mapper 时不得注入 ByScope：" + names);
	}

	@Test
	void null参数不注入ByScope() {
		List<String> names = methodNamesOf(new MySqlInjector(null), GoldenFixture.GoldenOrderMapper.class, GoldenFixture.GoldenOrder.class);
		assertFalse(hasScope(names), "null 参数不得注入 ByScope：" + names);
	}

	@Test
	void 配置FQN且命中接口链时注入ByScope() {
		List<String> names = methodNamesOf(new MySqlInjector(ScopeBaseMapper.class.getName()),
				GoldenFixture.GoldenOrderMapper.class, GoldenFixture.GoldenOrder.class);
		assertTrue(hasScope(names), "命中数据权限接口链应注入 ByScope：" + names);
	}

	@Test
	void 配置FQN但普通Mapper不命中则不注入() {
		List<String> names = methodNamesOf(new MySqlInjector(ScopeBaseMapper.class.getName()),
				GoldenFixture.GoldenUserMapper.class, GoldenFixture.GoldenUser.class);
		assertFalse(hasScope(names), "普通 BaseMapper 不得注入 ByScope：" + names);
	}
}

package com.xwang.gaia.autoconfigure;

import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import com.baomidou.mybatisplus.core.injector.AbstractSqlInjector;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.xwang.gaia.core.cache.CacheNameConsistencyChecker;
import com.xwang.gaia.core.mybatisplus.injector.MySqlInjector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配条件测试：SqlInjector 让位规则、checker 的 CacheManager 依赖与开关、
 * datascope 配置键到注入器的传递。
 */
class GaiaAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(GaiaAutoConfiguration.class));

	/** 真实应用形态：@EnableCaching + CacheAutoConfiguration 产出 CacheManager，验证自动配置排序不使 @ConditionalOnBean 失效 */
	private final ApplicationContextRunner bootCacheRunner = new ApplicationContextRunner()
			.withUserConfiguration(EnableCachingConfig.class)
			.withConfiguration(AutoConfigurations.of(
					CacheAutoConfiguration.class,
					GaiaAutoConfiguration.class))
			.withPropertyValues("spring.cache.type=simple");

	/** SimpleCacheConfiguration 要求 CacheAspectSupport（仅 @EnableCaching 注册），真实应用必带 */
	@Configuration
	@EnableCaching
	static class EnableCachingConfig {
	}

	@Test
	void 默认注册MySqlInjector且无CacheManager时不注册checker() {
		runner.run(ctx -> {
			assertThat(ctx).hasSingleBean(MySqlInjector.class);
			assertThat(ctx).doesNotHaveBean(CacheNameConsistencyChecker.class);
		});
	}

	@Test
	void 用户自定义SqlInjector时退避() {
		runner.withBean(AbstractSqlInjector.class, DefaultSqlInjector::new).run(ctx -> {
			assertThat(ctx).doesNotHaveBean(MySqlInjector.class);
			assertThat(ctx).hasSingleBean(AbstractSqlInjector.class);
		});
	}

	@Test
	void CacheAutoConfiguration提供的CacheManager也要注册checker() {
		bootCacheRunner.withPropertyValues("gaia.cache.name-prefix=demo_")
				.run(ctx -> assertThat(ctx).hasSingleBean(CacheNameConsistencyChecker.class));
	}

	@Test
	void 有CacheManager但未配置namePrefix时不注册checker() {
		runner.withBean(CacheManager.class, SimpleCacheManager::new)
				.run(ctx -> assertThat(ctx).doesNotHaveBean(CacheNameConsistencyChecker.class));
	}

	@Test
	void 有CacheManager且配置namePrefix时注册checker() {
		runner.withBean(CacheManager.class, SimpleCacheManager::new)
				.withPropertyValues("gaia.cache.name-prefix=demo_")
				.run(ctx -> assertThat(ctx).hasSingleBean(CacheNameConsistencyChecker.class));
	}

	@Test
	void enabled为false时不注册checker() {
		runner.withBean(CacheManager.class, SimpleCacheManager::new)
				.withPropertyValues("gaia.cache.name-prefix=demo_", "gaia.cache.consistency-checker.enabled=false")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(CacheNameConsistencyChecker.class));
	}

	@Test
	void datascope配置键传递到注入器() {
		runner.withPropertyValues("gaia.mybatis.datascope-mapper-class=com.xwang.gaia.testfix.ScopeBaseMapper")
				.run(ctx -> {
					MySqlInjector injector = ctx.getBean(MySqlInjector.class);
					Field f = MySqlInjector.class.getDeclaredField("datascopeMapperClass");
					f.setAccessible(true);
					assertThat(f.get(injector)).isEqualTo("com.xwang.gaia.testfix.ScopeBaseMapper");
				});
	}
}

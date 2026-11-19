package com.xwang.gaia.autoconfigure;

import com.baomidou.mybatisplus.core.injector.AbstractSqlInjector;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.xwang.gaia.core.cache.CacheNameConsistencyChecker;
import com.xwang.gaia.core.mybatisplus.injector.MySqlInjector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

/**
 * gaia 自动装配：MySqlInjector 与缓存名一致性检查器。
 * 宿主无 MyBatis-Plus 时整体退避（{@link ConditionalOnClass} 由 Boot 在元数据层判断，不触发类加载）。
 * 必须排在 CacheAutoConfiguration 之后：Boot 按类名字母序排自动配置，本类包名在前，
 * 不加 after 约束时 {@link ConditionalOnBean} 求值阶段 CacheManager 定义尚未注册。
 */
@AutoConfiguration(after = CacheAutoConfiguration.class)
@ConditionalOnClass(DefaultSqlInjector.class)
public class GaiaAutoConfiguration {

	/**
	 * gaia SQL 注入器：MP starter 经 ObjectProvider&lt;SqlInjector&gt; 自动采用。
	 * 用户已自带 SqlInjector 时让位。
	 *
	 * @param datascopeMapperClass 数据权限基 Mapper 全限定名，空=不注入 ByScope 方法
	 */
	@Bean
	@ConditionalOnMissingBean(AbstractSqlInjector.class)
	public MySqlInjector mySqlInjector(
			@Value("${gaia.mybatis.datascope-mapper-class:}") String datascopeMapperClass) {
		return new MySqlInjector(datascopeMapperClass);
	}

	/**
	 * 缓存名一致性检查器装配：@ConditionalOnProperty 在 Boot 3 不可重复且无容器注解，
	 * 用嵌套配置类承载 name-prefix 存在性条件，enabled 开关留在 Bean 方法上。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "gaia.cache.name-prefix")
	static class CacheNameConsistencyCheckerConfiguration {

		/**
		 * 启动期缓存名一致性自检：需宿主已启用缓存（存在 CacheManager）且显式配置
		 * {@code gaia.cache.name-prefix}（不设则不注册，避免对混合前缀应用产生噪音告警），
		 * 比对集合取 CacheManager.getCacheNames()（即显式配置过 TTL 的缓存名）。
		 *
		 * @param cacheNamePrefix 本模块缓存名前缀（如 store_）
		 */
		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnBean(CacheManager.class)
		@ConditionalOnProperty(name = "gaia.cache.consistency-checker.enabled", matchIfMissing = true)
		public CacheNameConsistencyChecker cacheNameConsistencyChecker(
				CacheManager cacheManager,
				@Value("${gaia.cache.name-prefix:}") String cacheNamePrefix) {
			return new CacheNameConsistencyChecker(new HashSet<>(cacheManager.getCacheNames()), cacheNamePrefix);
		}
	}
}

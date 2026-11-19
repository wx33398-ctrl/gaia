package com.xwang.gaia.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.util.ClassUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 启动期缓存名一致性自检：把容器内所有 {@code @Cacheable/@CachePut/@CacheEvict}（含 @Caching 包装与类级注解）
 * 引用的缓存名，与 RedisCacheConfig 显式配置了 TTL 的缓存名集合做双向 diff，
 * 不匹配项打 WARN 点名——把"名字拼错/未遵守 XxxEntity 命名约定导致 TTL 静默失效"变成启动日志中的显式信息。
 *
 * 接入方式（业务 RedisCacheConfig 中）：
 * <pre>
 * &#64;Bean
 * public CacheNameConsistencyChecker cacheNameConsistencyChecker(RedisCacheManager manager) {
 *     return new CacheNameConsistencyChecker(manager.getCacheConfigurations().keySet(), "store_");
 * }
 * </pre>
 */
public class CacheNameConsistencyChecker implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(CacheNameConsistencyChecker.class);

    /** 显式配置了 TTL 的缓存名快照（来自 RedisCacheManager.getCacheConfigurations） */
    private final Set<String> configuredNames;
    /** 本模块缓存名前缀，如 store_ / beauty_ / teaching_，用于过滤无关框架缓存 */
    private final String cacheNamePrefix;

    public CacheNameConsistencyChecker(Set<String> configuredNames, String cacheNamePrefix) {
        this.configuredNames = configuredNames;
        this.cacheNamePrefix = cacheNamePrefix;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof ConfigurableApplicationContext)) {
            return;
        }
        ConfigurableListableBeanFactory bf =
                ((ConfigurableApplicationContext) event.getApplicationContext()).getBeanFactory();
        Set<String> annotated = new HashSet<>();
        for (String beanName : bf.getBeanDefinitionNames()) {
            Class<?> type;
            try {
                type = bf.getType(beanName);
            } catch (Exception e) {
                continue;
            }
            if (type == null || type.getName().startsWith("org.springframework")) {
                continue;
            }
            annotated.addAll(collectNames(ClassUtils.getUserClass(type)));
        }
        long unconfigured = annotated.stream()
                .filter(n -> n.startsWith(cacheNamePrefix) && !configuredNames.contains(n))
                .peek(n -> log.warn("[CacheNameCheck] 缓存名 {} 被缓存注解引用但未配置显式TTL（走默认值；检查拼写、类名是否符合 XxxEntity 约定及 @TableType）", n))
                .count();
        long unreferenced = configuredNames.stream()
                .filter(n -> n.startsWith(cacheNamePrefix) && !annotated.contains(n))
                .peek(n -> log.warn("[CacheNameCheck] 缓存名 {} 已配置TTL但未被任何缓存注解引用（拼写不一致或仅供编程式 getCache 使用）", n))
                .count();
        if (unconfigured == 0 && unreferenced == 0) {
            log.info("[CacheNameCheck] 缓存名一致性检查通过：已配置 {} 个，注解引用 {} 个",
                    configuredNames.size(), annotated.size());
        }
    }

    /**
     * 收集类（含其全部方法与类级注解）上声明的缓存名。
     * 直接读取注解并手动展开 @Caching 容器，不依赖 @Repeatable 元数据
     * （spring-context 6.1.21 的注解类文件中元数据结构不完整，Spring 的
     * findMergedRepeatableAnnotations 会抛 IllegalArgumentException）；
     * value 与 cacheNames 两种写法都收集（JDK 原生注解代理不做 @AliasFor 合并）
     */
    static Set<String> collectNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        Set<AnnotatedElement> elements = new HashSet<>();
        elements.add(type);
        for (Method m : type.getMethods()) {
            elements.add(m);
        }
        for (Method m : type.getDeclaredMethods()) {
            elements.add(m);
        }
        for (AnnotatedElement el : elements) {
            Cacheable cacheable = el.getDeclaredAnnotation(Cacheable.class);
            if (cacheable != null) {
                collect(names, cacheable.value());
                collect(names, cacheable.cacheNames());
            }
            CachePut put = el.getDeclaredAnnotation(CachePut.class);
            if (put != null) {
                collect(names, put.value());
                collect(names, put.cacheNames());
            }
            CacheEvict evict = el.getDeclaredAnnotation(CacheEvict.class);
            if (evict != null) {
                collect(names, evict.value());
                collect(names, evict.cacheNames());
            }
            Caching caching = el.getDeclaredAnnotation(Caching.class);
            if (caching != null) {
                for (Cacheable c : caching.cacheable()) {
                    collect(names, c.value());
                    collect(names, c.cacheNames());
                }
                for (CachePut p : caching.put()) {
                    collect(names, p.value());
                    collect(names, p.cacheNames());
                }
                for (CacheEvict e : caching.evict()) {
                    collect(names, e.value());
                    collect(names, e.cacheNames());
                }
            }
        }
        return names;
    }

    private static void collect(Set<String> names, String... value) {
        for (String n : value) {
            if (n != null && !n.isEmpty()) {
                names.add(n);
            }
        }
    }
}

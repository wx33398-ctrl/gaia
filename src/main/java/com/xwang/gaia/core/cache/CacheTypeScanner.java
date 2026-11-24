package com.xwang.gaia.core.cache;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xwang.gaia.core.mybatisplus.annotation.TableType;
import com.xwang.gaia.core.mybatisplus.annotation.TableTypeEnum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描实体包，根据 {@link TableType} 注解构建缓存名集合。
 * 用于 RedisCacheConfig 自动决定缓存 TTL。
 */
public class CacheTypeScanner {

    private static final Logger log = LoggerFactory.getLogger(CacheTypeScanner.class);

    /**
     * 基于 Entity 类名扫描缓存名。
     * 优先使用 MyBatis-Plus {@link TableInfoHelper} 获取已注册的 Entity，
     * 若尚未初始化则降级为类文件扫描。
     * 缓存名格式：{prefix}_{className 去掉 Entity 后缀首字母小写}
     * 例如：GoodsLimitEntity → store_goodsLimit
     *
     * @param entityPackage 实体类所在包名（降级扫描时使用）
     * @param cachePrefix   缓存名前缀
     * @param tableType     表类型枚举
     * @return 缓存名集合
     */
    public static Set<String> scanCachesByClassName(String entityPackage, String cachePrefix, TableTypeEnum tableType) {
        Set<String> caches = scanByTableInfoHelper(entityPackage, cachePrefix, tableType);
        if (caches.isEmpty()) {
            caches = scanByClasses(entityPackage, cachePrefix, tableType);
        }
        return caches;
    }

    /**
     * 通过 MyBatis-Plus TableInfoHelper 扫描缓存名。
     * 要求 SqlSessionFactory 已初始化，否则返回空集合。
     */
    private static Set<String> scanByTableInfoHelper(String entityPackage, String cachePrefix, TableTypeEnum tableType) {
        try {
            List<TableInfo> tableInfos = TableInfoHelper.getTableInfos();
            if (tableInfos == null || tableInfos.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> caches = new HashSet<>();
            for (TableInfo tableInfo : tableInfos) {
                Class<?> entityClass = tableInfo.getEntityType();
                if (entityClass == null) continue;
                String packageName = entityClass.getPackageName();
                if (!packageName.startsWith(entityPackage)) continue;
                if (!entityClass.getSimpleName().endsWith("Entity")) continue;
                TableType annotation = entityClass.getAnnotation(TableType.class);
                if (annotation != null && annotation.value() == tableType) {
                    String cn = buildCacheNameFromClass(cachePrefix, entityClass.getSimpleName());
                    caches.add(cn);
                    log.info("[CacheTypeScanner] TableInfoHelper 匹配 [注解] - class: {}, cacheName: {}, tableType: {}", entityClass.getSimpleName(), cn, annotation.value());
                } else if (annotation == null && tableType == TableTypeEnum.BUSINESS) {
                    String cn = buildCacheNameFromClass(cachePrefix, entityClass.getSimpleName());
                    caches.add(cn);
                    log.info("[CacheTypeScanner] TableInfoHelper 匹配 [默认BUSINESS] - class: {}, cacheName: {}", entityClass.getSimpleName(), cn);
                }
            }
            return caches;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * 降级方案：通过类文件扫描缓存名。
     */
    private static Set<String> scanByClasses(String entityPackage, String cachePrefix, TableTypeEnum tableType) {
        String path = entityPackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            URL resource = classLoader.getResource(path);
            if (resource == null) {
                return Collections.emptySet();
            }
            Set<String> caches = new HashSet<>();
            String filePath = resource.getPath();
            for (Class<?> cls : scanClasses(classLoader, path, filePath, entityPackage)) {
                if (!cls.getSimpleName().endsWith("Entity")) continue;
                TableType annotation = cls.getAnnotation(TableType.class);
                if (annotation != null && annotation.value() == tableType) {
                    String cn = buildCacheNameFromClass(cachePrefix, cls.getSimpleName());
                    caches.add(cn);
                    log.info("[CacheTypeScanner] 降级扫描匹配 [注解] - class: {}, cacheName: {}, tableType: {}", cls.getSimpleName(), cn, annotation.value());
                } else if (annotation == null && tableType == TableTypeEnum.BUSINESS) {
                    String cn = buildCacheNameFromClass(cachePrefix, cls.getSimpleName());
                    caches.add(cn);
                    log.info("[CacheTypeScanner] 降级扫描匹配 [默认BUSINESS] - class: {}, cacheName: {}", cls.getSimpleName(), cn);
                }
            }
            return caches;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * 基于类名构建缓存名：{prefix}_{className 去掉 Entity 后缀首字母小写}
     */
    public static String buildCacheNameFromClass(String cachePrefix, String className) {
        String name = className;
        if (name.endsWith("Entity")) {
            name = name.substring(0, name.length() - 6);
        }
        return cachePrefix + "_" + Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 基于类名扫描 CONFIG 类型缓存名（快捷方法）
     */
    public static Set<String> scanConfigCachesByClassName(String entityPackage, String cachePrefix) {
        return scanCachesByClassName(entityPackage, cachePrefix, TableTypeEnum.CONFIG);
    }

    /**
     * 基于类名扫描 BUSINESS 类型缓存名（快捷方法，含未标注 @TableType 的实体）
     */
    public static Set<String> scanBusinessCachesByClassName(String entityPackage, String cachePrefix) {
        return scanCachesByClassName(entityPackage, cachePrefix, TableTypeEnum.BUSINESS);
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<?>> scanClasses(ClassLoader classLoader, String path, String filePath, String packageName) {
        Set<Class<?>> classes = new HashSet<>();
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return classes;
        }
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                classes.addAll(scanClasses(classLoader, path + '/' + file.getName(),
                        file.getAbsolutePath(), packageName + '.' + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> cls = classLoader.loadClass(className);
                    Type genericSuperclass = cls.getGenericSuperclass();
                    boolean isGenericEntity = genericSuperclass instanceof ParameterizedType;
                    if (isGenericEntity || (!cls.isInterface() && !cls.isAnnotation() && !cls.isEnum())) {
                        classes.add(cls);
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        return classes;
    }
}

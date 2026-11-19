# gaia-spring-boot-starter

通用 CRUD 基架：`BaseController / BaseService / BaseDTO / BaseVO` 四件套 + MyBatis-Plus 注解式查询增强 +
缓存名体系。零微服务依赖，单坐标引入，适用于任何 Spring Boot 3 项目。

## 环境要求

| 项 | 要求 |
|---|---|
| JDK | 21 |
| Spring Boot | 3.3.x（构建基线 3.3.13） |
| MyBatis-Plus | 3.5.9（与源库一致；`BaseService` 为独立接口，内置 IService 兼容区） |

> 宿主自带 `spring-boot-starter-web` 与 MP starter（`mybatis-plus-spring-boot3-starter`）；
> 框架对二者为 optional 依赖，版本由宿主决定。

> **JDK17+ 注意**：DTO↔Entity 拷贝走 mica-core BeanCopier（CGLIB），运行与测试需开放模块：
> `--add-opens=java.base/java.lang=ALL-UNNAMED`（测试已在 surefire 配好，业务应用运行期同样需要）。

## 快速开始

```xml
<dependency>
    <groupId>com.xwang</groupId>
    <artifactId>gaia-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

四层模板（以 Goods 为例，`@Mapper` 注解在 Mapper 上，与 MP 惯例一致）：

```java
// 实体：约定见下节
@TableName("goods")
public class GoodsEntity { @TableId private Long id; private String name; /* getter/setter */ }

// DTO：继承 BaseDTO，自指泛型；分页/排序参数已内置（current/size/ascs/descs/orders）
public class GoodsDTO extends BaseDTO<GoodsDTO> { private String name; }

// VO：实现 BaseVO，of() 拷贝后回调 afterPropertySet() 做派生字段
public class GoodsVO implements BaseVO<GoodsVO> { private Long id; private String name; }

// Mapper
@Mapper
public interface GoodsMapper extends BaseMapper<GoodsEntity> {}

// Service 接口：两泛型 实体/DTO
public interface GoodsService extends BaseService<GoodsEntity, GoodsDTO> {}

// Service 实现：继承 MP ServiceImpl 获得 getLog()，并覆写 getMapper()
@Service
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, GoodsEntity> implements GoodsService {
    @Override
    public BaseMapper<GoodsEntity> getMapper() {
        return this.baseMapper;
    }
}

// Controller：三泛型 实体/DTO/VO，继承即得 8 个通用端点
@RestController
@RequestMapping("/goods")
public class GoodsController extends BaseController<GoodsEntity, GoodsDTO, GoodsVO> {
    @Autowired
    private GoodsService goodsService;

    @Override
    protected BaseService<GoodsEntity, GoodsDTO> getService() {
        return this.goodsService;
    }
}
```

继承即得（在方法上加 `@GetMapping/@PostMapping` 等即可暴露）：
`addEntity`（返回主键）、`addBatch`、`modifyEntity`、`modifyBatch`、`removeEntity(id)`、`removeBatch(idList)`、
`queryDetail(id)`（查不到返回 10104）、`queryPage(dto)`（data 为 `{total,pages,size,current,records}` 分页包装，records 已转 VO）。

统一响应 `R<T>`：`code=0` 成功；业务异常 `BusinessException.Status`（10101~10108）。

### 查询增强注解（Entity 字段上声明，`queryPage/queryList` 自动生效）

| 注解 | 作用 |
|---|---|
| `@Compare` | 等值/比较条件（值非空才拼接） |
| `@In` | IN 条件（集合非空才拼接） |
| `@StartTime` / `@EndTime` | 时间范围（`>=` / `<=`） |
| `@Relation` | 关联查询列（动态 JOIN，`columnStr` 原样拼入 SELECT，取值须来自代码而非请求参数） |
| `@Aggregate` | 聚合子查询列（**仅 `selectById` 生效**，列表/分页不拼） |

## 实体约定（替代 BaseEntity）

框架**不提供** BaseEntity，审计字段按模板逐个实体声明（便于按需取舍列）：

```java
@TableName("goods")
public class GoodsEntity {
    @TableId
    private Long id;
    private String name;

    // ---- 审计六字段（填充逻辑见下）----
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private String createByName;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateByName;

    // ---- 查询辅助字段（不映射表列，配合查询增强注解）----
    @TableField(exist = false)
    @Compare(tableField = "name")
    private String nameLike;
}
```

填充由业务侧实现 MP 的 `MetaObjectHandler`（框架不绑定用户来源，`createBy` 取值自你的登录上下文）：

```java
@Component
public class AppMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(metaObject, "createBy", String.class, currentUsername());
        // ...
    }
    @Override
    public void updateFill(MetaObject metaObject) { /* 同上，update 系字段 */ }
}
```

## 缓存体系

实体类名派生缓存名：`{prefix}_{类名去 Entity 后缀首字母小写}`（`GoodsLimitEntity` → `demo_goodsLimit`）。
`@TableType(TableTypeEnum.CONFIG)` 标字典类实体（长 TTL），未标注默认 BUSINESS（短 TTL）。

`RedisCacheConfig` 样例（TTL 分组 + 启动期一致性自检）：

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        RedisCacheConfiguration configTtl = RedisCacheConfiguration.defaultCacheConfig().ttl(Duration.ofHours(1));
        RedisCacheConfiguration businessTtl = RedisCacheConfiguration.defaultCacheConfig().ttl(Duration.ofSeconds(1));
        CacheTypeScanner.scanConfigCachesByClassName("com.demo", "demo_")
                .forEach(name -> configs.put(name, configTtl));
        CacheTypeScanner.scanBusinessCachesByClassName("com.demo", "demo_")
                .forEach(name -> configs.put(name, businessTtl));
        return RedisCacheManager.builder(factory)
                .initialCacheConfigurations(configs)
                .build();
    }
}
```

一致性检查器（比对 `@Cacheable/@CacheEvict/@CachePut` 引用的缓存名与已配置 TTL 的缓存名，不匹配打 WARN）
已自动装配：宿主启用缓存（存在 `CacheManager`）并配置前缀即生效：

```yaml
gaia:
  cache:
    name-prefix: demo_          # 不设则不注册检查器
    consistency-checker:
      enabled: true             # 默认 true
```

## clearCache() 钩子边界

`BaseService.clearCache()` 是**供业务覆写的手工钩子**（默认空实现）：框架的任何写操作都**不会**自动调用它
（与源库契约一致）。需要写后缓存失效时，在业务写路径上显式调用 `clearCache()`，或在你自己的
`addEntity/modifyEntity/...` 覆写里编排调用时机。

## 数据权限扩展点（ByScope）

配置数据权限基 Mapper 后，注入器为继承它的 Mapper 追加 `selectListByScope / selectPageByScope`
（SQL 中占位 `SCOPE_FILTER`，由你的 MP 拦截器填充）：

```yaml
gaia:
  mybatis:
    datascope-mapper-class: com.demo.mapper.MyScopeBaseMapper
```

不配置（默认）不注入任何 ByScope 方法。

## FAQ

- **Long 主键精度**：框架不内置 Jackson 定制，建议宿主全局配置 Long→String 序列化
  （`Jackson2ObjectMapperBuilderCustomizer` 注册 `ToStringSerializer(Long.class)`）。
- **MP 版本**：按 3.5.9 构建（与源库一致）；`BaseService` 独立接口 + 内置 IService 兼容区，不依赖 `Db` 桥接 API。
- **分页上限**：`getPage()` 把 `size` 钳到上限 1000（`null` 或 `<1` 走默认 10）、`current<1` 钳到 1。`size<1` 不再透传——MP 的 `size<0 不分页` 要求 `maxLimit` 为 null，而本框架 `maxLimit` 默认 `0L`，透传 `-1` 会拼出 `LIMIT -1`（MySQL 1064）。`maxLimit` 仅在 `>0` 时下发，否则传 null，避免覆盖宿主在分页拦截器上配置的全局上限。
- **排序注入防护**：`ascs/descs` 经 `SqlInjectionUtils` 关键字/注释特征拦截，命中抛 `IllegalArgumentException`；不做实体列白名单，合法标识符的未知列透传给 SQL 层。

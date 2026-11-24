package com.xwang.gaia.core.base;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 框架安全守卫回归测试：排序注入拦截（SqlInjectionUtils）与分页参数钳制。
 * 按框架约定，实现类通过业务接口继承 BaseService 携带泛型。
 */
class BaseServiceTest {

	@TableName("test_goods")
	public static class GoodsEntity {
		@TableId
		private Long id;
		private String name;
		private LocalDateTime createTime;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public LocalDateTime getCreateTime() {
			return createTime;
		}

		public void setCreateTime(LocalDateTime createTime) {
			this.createTime = createTime;
		}
	}

	public static class GoodsDTO extends BaseDTO<GoodsDTO> {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public interface GoodsService extends BaseService<GoodsEntity, GoodsDTO> {
	}

	static class GoodsServiceImpl implements GoodsService {
		@Override
		public BaseMapper<GoodsEntity> getMapper() {
			// 守卫通过后才会触达 mapper，测试只验证守卫之前的行为
			return null;
		}

		@Override
		public Log getLog() {
			return new NoLoggingImpl("test");
		}
	}

	@BeforeAll
	static void initTableInfo() {
		// 脱离 Spring 初始化实体元数据，供排序白名单使用
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
		assistant.setCurrentNamespace("com.xwang.test.GoodsMapper");
		TableInfoHelper.initTableInfo(assistant, GoodsEntity.class);
	}

	private final GoodsService service = new GoodsServiceImpl();

	@Test
	void applySorting接受实体已知列() {
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setAscs("createTime");
		service.applySorting(query, dto);
		String orderBy = query.getSqlSegment();
		assertTrue(orderBy.contains("create_time"), orderBy);
		assertTrue(orderBy.contains("ASC"), orderBy);
	}

	@Test
	void applySortingAscs与Descs独立生效() {
		// 宿主契约（shop-admin 商品列表 sort_order ASC + id DESC 组合排序）：ascs 与 descs 同时传均生效，
		// 排序列带主表别名 t. 前缀（join 下裸列 1052 ambiguous）
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setAscs("createTime");
		dto.setDescs("name");
		service.applySorting(query, dto);
		String orderBy = query.getSqlSegment();
		assertTrue(orderBy.contains("t.create_time ASC"), orderBy);
		assertTrue(orderBy.contains("t.name DESC"), orderBy);
	}

	@Test
	void applySorting对Orders列加前缀() {
		// orders 经 Page 直达分页拦截器原样拼 ORDER BY，校验后就地改写为 t. 前缀
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setOrders(new ArrayList<>(List.of(OrderItem.desc("createTime"))));
		service.applySorting(query, dto);
		assertEquals("t.create_time", dto.getOrders().get(0).getColumn());
	}

	@Test
	void applyMaxLimit下推LIMIT并兜底() {
		// maxLimit>0 下推 LIMIT；未设时兜底 LIMIT 1000；超 MAX_LIMIT 钳到 50000
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setMaxLimit(5000L);
		service.applyMaxLimit(query, dto);
		assertTrue(query.getSqlSegment().contains("LIMIT 5000"), query.getSqlSegment());

		QueryWrapper<GoodsEntity> query2 = new QueryWrapper<>();
		service.applyMaxLimit(query2, new GoodsDTO());
		assertTrue(query2.getSqlSegment().contains("LIMIT 1000"), query2.getSqlSegment());

		QueryWrapper<GoodsEntity> query3 = new QueryWrapper<>();
		GoodsDTO big = new GoodsDTO();
		big.setMaxLimit(999_999L);
		service.applyMaxLimit(query3, big);
		assertTrue(query3.getSqlSegment().contains("LIMIT 50000"), query3.getSqlSegment());
	}

	@Test
	void applySorting拒绝注入payload() {
		// 源库语义：SqlInjectionUtils 关键字/注释特征拦截（非实体列白名单）
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setAscs("1' union select pwd from mysql.user");
		assertThrows(IllegalArgumentException.class, () -> service.applySorting(query, dto));

		GoodsDTO dto2 = new GoodsDTO();
		dto2.setDescs("id;select pwd from mysql.user");
		assertThrows(IllegalArgumentException.class, () -> service.applySorting(query, dto2));
	}

	@Test
	void applySorting不校验列归属() {
		// 源库语义：只拦注入特征，不做实体列白名单；合法标识符的未知列透传给 SQL 层处理
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setDescs("pwd");
		assertDoesNotThrow(() -> service.applySorting(query, dto));
		assertTrue(query.getSqlSegment().contains("pwd"));
	}

	@Test
	void applySorting不处理orders() {
		// 源库语义：orders 走 Page 对象透传，applySorting 只处理 ascs/descs
		QueryWrapper<GoodsEntity> query = new QueryWrapper<>();
		GoodsDTO dto = new GoodsDTO();
		dto.setOrders(List.of(OrderItem.asc("name")));
		assertDoesNotThrow(() -> service.applySorting(query, dto));
		assertTrue(query.getSqlSegment().isEmpty());
	}

	@Test
	void getPage钳制分页参数() {
		GoodsDTO dto = new GoodsDTO();
		IPage<?> page = dto.getPage();
		assertEquals(1L, page.getCurrent());
		assertEquals(10L, page.getSize());

		dto.setSize(999_999L);
		assertEquals(BaseDTO.MAX_PAGE_SIZE, dto.getPage().getSize());

		// size<1 视为未指定，走默认 10：透传 -1 会让 MP 拼出 `LIMIT -1` → MySQL 1064
		// （旧「size<0 不分页」在本框架从未生效，maxLimit 默认 0L 非 null 已先短路掉 MP 的不分页分支）
		dto.setSize(-1L);
		assertEquals(10L, dto.getPage().getSize());
		dto.setSize(0L);
		assertEquals(10L, dto.getPage().getSize());

		dto.setSize(20L);
		dto.setCurrent(3L);
		assertEquals(20L, dto.getPage().getSize());
		assertEquals(3L, dto.getPage().getCurrent());

		// null 走默认值不 NPE（设计文档批准项）
		dto.setSize(null);
		dto.setCurrent(null);
		assertEquals(10L, dto.getPage().getSize());
		assertEquals(1L, dto.getPage().getCurrent());
	}

	@Test
	void maxLimit仅在大于0时下推() {
		// 0L 下推会覆盖宿主在分页拦截器上配置的全局 maxLimit，故 null 才是「不覆盖」语义
		GoodsDTO dto = new GoodsDTO();
		assertNull(dto.getPage().maxLimit(), "默认 maxLimit=0 不得下推");

		dto.setMaxLimit(0L);
		assertNull(dto.getPage().maxLimit(), "显式 0 同样不下推");

		dto.setMaxLimit(500L);
		assertEquals(500L, dto.getPage().maxLimit().longValue());
	}

	@Test
	void 查询钩子对count与one与条件删除同样生效() {
		// 回归锁：queryCount/queryOne/removeEntity(D) 必须与 queryPage/queryList 一致走 convertToEntityForQuery，
		// 否则宿主覆写查询预处理后，count 与 page 条件背离、条件删除删多删少
		AtomicReference<Object> captured = new AtomicReference<>();
		BaseMapper<GoodsEntity> mapper = (BaseMapper<GoodsEntity>) Proxy.newProxyInstance(
				BaseMapper.class.getClassLoader(), new Class<?>[]{BaseMapper.class},
				(proxy, method, args) -> {
					if (args != null && args.length > 0 && args[0] instanceof QueryWrapper<?> qw) {
						captured.set(qw.getEntity());
					}
					Class<?> rt = method.getReturnType();
					if (rt == long.class || rt == Long.class) return 0L;
					if (rt == int.class || rt == Integer.class) return 0;
					return null;
				});
		GoodsService hooked = new GoodsServiceImpl() {
			@Override
			public BaseMapper<GoodsEntity> getMapper() {
				return mapper;
			}

			@Override
			public GoodsEntity convertToEntityForQuery(GoodsDTO dto) {
				GoodsEntity e = new GoodsEntity();
				e.setName("hooked:" + dto.getName());
				return e;
			}
		};
		GoodsDTO dto = new GoodsDTO();
		dto.setName("a");

		hooked.queryCount(dto);
		assertEquals("hooked:a", ((GoodsEntity) captured.get()).getName());

		hooked.queryOne(dto);
		assertEquals("hooked:a", ((GoodsEntity) captured.get()).getName());

		hooked.removeEntity(dto);
		assertEquals("hooked:a", ((GoodsEntity) captured.get()).getName());
	}
}

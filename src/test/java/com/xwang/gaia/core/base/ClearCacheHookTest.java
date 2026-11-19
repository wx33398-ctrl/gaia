package com.xwang.gaia.core.base;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * clearCache 钩子语义测试（源库契约）：
 * clearCache 是供业务覆写的手工钩子，框架的任何写操作都不会自动触发它。
 */
class ClearCacheHookTest {

	@TableName("hook_order")
	public static class HookOrder {
		@TableId
		private Long id;
		private String name;

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
	}

	public static class HookDTO extends BaseDTO<HookDTO> {
		private Long id;
		private String name;

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
	}

	public interface HookService extends BaseService<HookOrder, HookDTO> {
	}

	/** 计数服务：clearCache 计数 + mapper 行为可控 */
	static class CountingService implements HookService {
		final AtomicInteger evicts = new AtomicInteger();

		@Override
		public BaseMapper<HookOrder> getMapper() {
			return (BaseMapper<HookOrder>) Proxy.newProxyInstance(
					ClearCacheHookTest.class.getClassLoader(), new Class<?>[]{BaseMapper.class},
					(proxy, method, args) -> {
						switch (method.getName()) {
							case "insert":
							case "updateById":
							case "delete":
							case "deleteById":
							case "deleteBatchIds":
								return 1;
							default:
								Class<?> rt = method.getReturnType();
								if (rt == boolean.class) {
									return false;
								}
								if (rt == int.class) {
									return 0;
								}
								if (rt == long.class || rt == Long.class) {
									return 0L;
								}
								return null;
						}
					});
		}

		@Override
		public Log getLog() {
			return LogFactory.getLog(CountingService.class);
		}

		@Override
		public void clearCache() {
			evicts.incrementAndGet();
		}
	}

	@BeforeAll
	static void initTableInfo() {
		MybatisConfiguration configuration = new MybatisConfiguration();
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), HookOrder.class);
	}

	CountingService svc;

	@BeforeEach
	void setUp() {
		svc = new CountingService();
	}

	@Test
	void 顶层写操作不自动触发clearCache() {
		HookDTO dto = new HookDTO();
		dto.setName("n");
		svc.addEntity(dto);
		dto.setId(1L);
		assertTrue(svc.modifyEntity(dto));
		assertTrue(svc.removeEntity(1L));
		svc.removeEntity(dto);
		svc.removeBatch(List.of(1L, 2L));
		HookOrder entity = new HookOrder();
		entity.setName("n");
		svc.saveOrUpdate(entity);
		assertEquals(0, svc.evicts.get(), "源库契约：框架不得自动触发业务缓存钩子");
	}

	@Test
	void 手工调用clearCache生效() {
		svc.clearCache();
		assertEquals(1, svc.evicts.get());
	}
}

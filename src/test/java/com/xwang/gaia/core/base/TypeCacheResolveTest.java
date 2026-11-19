package com.xwang.gaia.core.base;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 泛型契约测试（源库语义）：实现类通过业务接口继承 BaseService 携带 E/D 两泛型，
 * TypeCache 经「实现类 → 业务接口 → BaseService<E,D>」两层解析并缓存。
 */
class TypeCacheResolveTest {

	@TableName("resolve_str_key")
	public static class StrKeyEntity {
		@TableId
		private String id;
		private String code;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}
	}

	public static class StrKeyDTO extends BaseDTO<StrKeyDTO> {
		private String code;

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}
	}

	// ---- 正确形态：实现类 implements 业务接口，业务接口 extends BaseService<E,D> ----
	public interface ResolveService extends BaseService<StrKeyEntity, StrKeyDTO> {
	}

	static class ResolveServiceImpl implements ResolveService {
		@Override
		public BaseMapper<StrKeyEntity> getMapper() {
			return null;
		}

		@Override
		public Log getLog() {
			return new NoLoggingImpl("test");
		}
	}

	@Test
	void 业务接口继承BaseService可解析两泛型() {
		ResolveService service = new ResolveServiceImpl();
		BaseService.TypeCache<?, ?> cache = assertDoesNotThrow(service::getTypeCache);
		assertEquals(StrKeyEntity.class, cache.getClassE());
		assertEquals(StrKeyDTO.class, cache.getClassD());
	}

}

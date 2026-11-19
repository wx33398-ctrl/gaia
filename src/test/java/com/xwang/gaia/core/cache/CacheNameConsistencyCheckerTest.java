package com.xwang.gaia.core.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存名收集逻辑测试：类级/方法级/重复注解 @Caching 均能收集，无注解不产生噪音
 */
class CacheNameConsistencyCheckerTest {

	@Cacheable("cls_level")
	static class Holder {

		@Cacheable("goods_a")
		void query() {
		}

		@CacheEvict(value = "goods_b", beforeInvocation = true)
		void remove() {
		}

		@Caching(put = {@CachePut("goods_c"), @CachePut("goods_d")}, evict = @CacheEvict("goods_e"))
		void modify() {
		}

		void plain() {
		}
	}

	static class Plain {
		void noop() {
		}
	}

	@Test
	void 收集类级与方法级缓存名() {
		Set<String> names = CacheNameConsistencyChecker.collectNames(Holder.class);
		assertEquals(Set.of("cls_level", "goods_a", "goods_b", "goods_c", "goods_d", "goods_e"), names);
	}

	@Test
	void 无注解类不产生缓存名() {
		assertTrue(CacheNameConsistencyChecker.collectNames(Plain.class).isEmpty());
	}
}

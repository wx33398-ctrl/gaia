package com.xwang.gaia.core.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 缓存名派生规则钉桩：{prefix}_{类名去 Entity 后缀首字母小写}
 */
class CacheTypeScannerTest {

	@Test
	void 去Entity后缀并首字母小写() {
		assertEquals("store_goodsLimit", CacheTypeScanner.buildCacheNameFromClass("store", "GoodsLimitEntity"));
	}

	@Test
	void 无Entity后缀仅首字母小写() {
		assertEquals("beauty_service", CacheTypeScanner.buildCacheNameFromClass("beauty", "Service"));
	}
}

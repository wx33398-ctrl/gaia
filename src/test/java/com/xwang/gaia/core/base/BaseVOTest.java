package com.xwang.gaia.core.base;

import lombok.Data;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseVO 属性复制与 afterPropertySet 回调测试
 */
class BaseVOTest {

	@Data
	static class OrderEntity {
		private Long id;
		private String name;
	}

	@Data
	static class OrderVO implements BaseVO<OrderVO> {
		private Long id;
		private String name;
		/** afterPropertySet 回调标记 */
		private String label;

		@Override
		public void afterPropertySet() {
			this.label = "VO-" + this.name;
		}
	}

	@Test
	void of复制同名字段并触发回调() {
		OrderEntity e = new OrderEntity();
		e.setId(7L);
		e.setName("abc");

		OrderVO vo = new OrderVO().of(e);

		assertEquals(7L, vo.getId());
		assertEquals("abc", vo.getName());
		assertEquals("VO-abc", vo.getLabel());
	}

	@Test
	void of传入null返回null且不回调() {
		assertNull(new OrderVO().of(null));
	}
}

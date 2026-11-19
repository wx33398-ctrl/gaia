package com.xwang.gaia.core.base;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseDTO 分页钳制与内部字段序列化行为测试
 */
class BaseDTOTest {

	/** 具体子类，用于实例化自指泛型的抽象父类 */
	static class OrderDTO extends BaseDTO<OrderDTO> {
	}

	@Test
	void 超大size被钳制到上限1000() {
		OrderDTO dto = new OrderDTO();
		dto.setSize(5000L);
		IPage<?> page = dto.getPage();
		assertEquals(1000L, page.getSize());
	}

	@Test
	void size为null走默认10() {
		OrderDTO dto = new OrderDTO();
		dto.setSize(null);
		assertEquals(10L, dto.getPage().getSize());
	}

	@Test
	void current非法或为空时钳制为1() {
		OrderDTO dto = new OrderDTO();
		dto.setCurrent(0L);
		assertEquals(1L, dto.getPage().getCurrent());
		dto.setCurrent(null);
		assertEquals(1L, dto.getPage().getCurrent());
	}

	@Test
	void 序列化不输出内部分页控制字段() throws Exception {
		String json = new ObjectMapper().writeValueAsString(new OrderDTO());
		assertFalse(json.contains("maxLimit"), "响应不得含 maxLimit: " + json);
		assertFalse(json.contains("optimizeCountSql"), "响应不得含 optimizeCountSql: " + json);
		assertFalse(json.contains("isSearchCount"), "响应不得含 isSearchCount: " + json);
		assertFalse(json.contains("searchCount"), "响应不得含 searchCount 别名: " + json);
	}

	@Test
	void 反序列化可写入内部分页控制字段() throws Exception {
		String json = "{\"maxLimit\":100,\"optimizeCountSql\":false,\"isSearchCount\":false}";
		OrderDTO dto = new ObjectMapper().readValue(json, OrderDTO.class);
		assertEquals(100L, dto.getMaxLimit());
		assertFalse(dto.isOptimizeCountSql());
		assertFalse(dto.isSearchCount());
	}
}

package com.xwang.gaia.core.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xwang.gaia.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R 统一响应行为测试：success/failure 家族与分页自动包装
 */
class RTest {

	@Test
	void success返回零码success消息与空数据() {
		R<?> r = R.success();
		assertEquals(0, r.getCode());
		assertEquals("success", r.getMsg());
		assertNull(r.getData());
		assertTrue(r.isSuccess());
	}

	@Test
	void success每次返回新实例避免共享可变单例被污染() {
		// 回归锁：曾经 success() 返回 static 常量，一处 setMsg 污染全 JVM
		R<Void> a = R.success();
		R<Void> b = R.success();
		assertNotSame(a, b);
		a.setMsg("hacked");
		assertEquals("success", b.getMsg());
		assertNotSame(R.success(null), R.success());
	}

	@Test
	void success携带数据时原样放入data() {
		R<String> r = R.success("hello");
		assertEquals(0, r.getCode());
		assertEquals("hello", r.getData());
	}

	@Test
	void success对IPage自动包装五分页字段并逐条转换() {
		Page<String> page = new Page<>(2, 10);
		page.setTotal(23);
		List<String> records = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			records.add("r" + i);
		}
		page.setRecords(records);

		R r = R.success(page, (String s) -> s.toUpperCase());

		assertEquals(0, r.getCode());
		assertInstanceOf(R.Data.class, r.getData());
		R.Data data = (R.Data) r.getData();
		assertEquals(23, data.getTotal());
		assertEquals(3, data.getPages());
		assertEquals(10, data.getSize());
		assertEquals(2, data.getCurrent());
		assertEquals(10, data.getRecords().size());
		assertEquals("R0", data.getRecords().get(0));
	}

	@Test
	void success对空分页records回空列表而非null() {
		// 出参契约：空结果 records=[] 而非 null，前端免判空
		Page<String> page = new Page<>(1, 10);
		page.setTotal(0);
		page.setRecords(new ArrayList<>());

		R.Data data = (R.Data) R.success(page, (String s) -> s).getData();
		assertNotNull(data.getRecords());
		assertTrue(data.getRecords().isEmpty());

		R.Data data2 = (R.Data) R.success(page, (String s) -> s, s -> { }).getData();
		assertNotNull(data2.getRecords());
		assertTrue(data2.getRecords().isEmpty());
	}

	@Test
	void failure取异常状态码与消息() {
		R r = R.failure(BusinessException.Status.NOT_FOUND_RECORD);
		assertEquals(10104, r.getCode());
		assertEquals("记录不存在", r.getMsg());
		assertFalse(r.isSuccess());
	}

	@Test
	void failure自定义消息覆盖默认消息() {
		R r = R.failure(BusinessException.Status.DATA_INSERT_ERROR, "插入失败：主键冲突");
		assertEquals(10101, r.getCode());
		assertEquals("插入失败：主键冲突", r.getMsg());
	}

	@Test
	void 业务异常携带状态且消息取自状态() {
		BusinessException e = new BusinessException(BusinessException.Status.BEAN_COPY_ERROR);
		assertEquals(BusinessException.Status.BEAN_COPY_ERROR, e.getStatus());
		assertEquals("对象拷贝异常", e.getMessage());
	}
}

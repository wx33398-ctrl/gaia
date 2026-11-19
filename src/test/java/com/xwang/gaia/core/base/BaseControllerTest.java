package com.xwang.gaia.core.base;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.junit.jupiter.api.Assertions;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xwang.gaia.core.util.R;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BaseController 契约测试：
 * 1) 构造器泛型解析（源库语义：直接子类必须给出三泛型具体类型）；
 * 2) addEntity 透传 service 返回的原始主键对象（R&lt;Object&gt;，不做字符串化）；
 * 3) queryDetail 转换语义不回归（of 拷贝 + afterPropertySet）；
 * 4) getCacheKey TreeMap 排序 + 显式 UTF-8（中文值钉桩，钉住不随平台默认字符集与字段声明序漂移）；
 * 5) 无 getModuleName（common-log 反射契约已随独立化移除）。
 */
class BaseControllerTest {

	// **** 测试模型 **** //

	public static class CtlEntity {
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

	public static class CtlDTO extends BaseDTO<CtlDTO> {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class CtlVO implements BaseVO<CtlVO> {
		private Long id;
		private String name;
		/** of 拷贝后回调标记 */
		private boolean afterSet;

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

		public boolean isAfterSet() {
			return afterSet;
		}

		@Override
		public void afterPropertySet() {
			this.afterSet = true;
		}
	}

	static class CtlService implements BaseService<CtlEntity, CtlDTO> {
		BaseMapper<CtlEntity> mapper;
		CtlEntity detail;
		Long newId;

		@Override
		public BaseMapper<CtlEntity> getMapper() {
			return mapper;
		}

		@Override
		public Log getLog() {
			return LogFactory.getLog(CtlService.class);
		}

		@Override
		public Long addEntity(CtlDTO dto) {
			return newId;
		}

		@Override
		public CtlEntity queryDetail(Serializable id) {
			return detail;
		}
	}

	static class LeafController extends BaseController<CtlEntity, CtlDTO, CtlVO> {
		final CtlService service = new CtlService();

		@Override
		protected BaseService<CtlEntity, CtlDTO> getService() {
			return service;
		}
	}

	// **** 泛型解析 **** //

	@Test
	void 直接子类三泛型解析正确() {
		LeafController ctl = new LeafController();
		assertSame(CtlEntity.class, ctl.getClassE());
		assertSame(CtlVO.class, ctl.getClassV());
	}

	// **** addEntity 契约 **** //

	@Test
	void addEntity透传原始主键_Long主键() {
		LeafController ctl = new LeafController();
		ctl.service.newId = Long.MAX_VALUE;
		R<Object> r = ctl.addEntity(new CtlDTO());
		assertEquals(Long.MAX_VALUE, r.getData());
	}

	@Test
	void addEntity无主键data为null() {
		LeafController ctl = new LeafController();
		ctl.service.newId = null;
		R<Object> r = ctl.addEntity(new CtlDTO());
		assertNull(r.getData());
	}

	// **** queryDetail 语义钉桩 **** //

	@Test
	void queryDetail拷贝并回调afterPropertySet() {
		LeafController ctl = new LeafController();
		CtlEntity e = new CtlEntity();
		e.setId(7L);
		e.setName("张三");
		ctl.service.detail = e;
		R<CtlVO> r = ctl.queryDetail(7L);
		assertEquals(7L, r.getData().getId());
		assertEquals("张三", r.getData().getName());
		Assertions.assertTrue(r.getData().isAfterSet());
	}

	@Test
	void queryDetail不存在返回NOT_FOUND() {
		LeafController ctl = new LeafController();
		ctl.service.detail = null;
		R<CtlVO> r = ctl.queryDetail(1L);
		Assertions.assertFalse(r.isSuccess());
	}

	// **** getCacheKey TreeMap+UTF-8 钉桩 **** //

	@Test
	void getCacheKey显式UTF8钉桩() {
		LeafController ctl = new LeafController();
		CtlDTO dto = new CtlDTO();
		dto.setName("张三");
		String key = ctl.getCacheKey(dto);
		// 完整钉桩：非空过滤 + BaseDTO 分页默认值参与 + TreeMap 键名字典序 + UTF-8 字节 md5（特征化测试）
		String full = "{current=1, isSearchCount=true, maxLimit=0, name=张三, optimizeCountSql=true, orders=[], size=10}";
		String utf8 = DigestUtils.md5DigestAsHex(full.getBytes(StandardCharsets.UTF_8));
		String gbk = DigestUtils.md5DigestAsHex(full.getBytes(Charset.forName("GBK")));
		assertEquals(utf8, key, "缓存键必须按 UTF-8 字节计算");
		assertNotEquals(gbk, key);
		// 空值过滤：name 为空不进键；两次调用稳定
		String empty = "{current=1, isSearchCount=true, maxLimit=0, optimizeCountSql=true, orders=[], size=10}";
		String emptyKey = ctl.getCacheKey(new CtlDTO());
		assertEquals(DigestUtils.md5DigestAsHex(empty.getBytes(StandardCharsets.UTF_8)), emptyKey);
		assertEquals(ctl.getCacheKey(new CtlDTO()), emptyKey);
	}

	@Test
	void getCacheKey空串与null同键() {
		LeafController ctl = new LeafController();
		CtlDTO nullName = new CtlDTO();
		CtlDTO blankName = new CtlDTO();
		blankName.setName("");
		assertEquals(ctl.getCacheKey(nullName), ctl.getCacheKey(blankName), "空串与 null 一样不参与键");
	}

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void getCacheKey与字段声明顺序无关() {
		// 两个 DTO 类字段声明顺序相反、值相同 → 键必须相同（TreeMap 按字段名排序）
		OrderA a = new OrderA();
		a.setX("1");
		a.setY("2");
		OrderB b = new OrderB();
		b.setY("2");
		b.setX("1");
		// getCacheKey 只反射 dto 字段，与 D 无关；裸类型跨 DTO 类计算
		BaseController ctl = new LeafController();
		assertEquals(ctl.getCacheKey(a), ctl.getCacheKey(b));
	}

	// **** common-log 反射契约已移除 **** //

	@Test
	void BaseController不再有getModuleName() {
		assertThrows(NoSuchMethodException.class, () -> BaseController.class.getMethod("getModuleName", int.class));
	}

	// **** 字段声明序对照模型 **** //

	public static class OrderA extends BaseDTO<OrderA> {
		private String x;
		private String y;

		public String getX() {
			return x;
		}

		public void setX(String x) {
			this.x = x;
		}

		public String getY() {
			return y;
		}

		public void setY(String y) {
			this.y = y;
		}
	}

	public static class OrderB extends BaseDTO<OrderB> {
		private String y;
		private String x;

		public String getX() {
			return x;
		}

		public void setX(String x) {
			this.x = x;
		}

		public String getY() {
			return y;
		}

		public void setY(String y) {
			this.y = y;
		}
	}
}

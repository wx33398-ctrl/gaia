package com.xwang.gaia.core.base;

import com.xwang.gaia.core.util.R;
import org.apache.ibatis.logging.LogFactory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.logging.Log;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collection;
import java.util.List;

/**
 * 编译 fixture（设计 §5 不变式）：本文件内的类模拟代码生成器产出形态——
 * DTO 泛型风格、两泛型 Service 接口、Impl 覆写 getMapper()/getLog()、
 * Controller 调用 addBatch。
 * 本文件编译通过 = gaia 对生成代码形态保持兼容；框架改动若破坏形态，CI 编译即红。
 */
class FixtureGeneratedStyleTest {

	// **** 模拟生成的实体/DTO/VO **** //

	public static class FixtureEntity {
		private Long id;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}
	}

	public static class FixtureDTO extends BaseDTO<FixtureDTO> {
	}

	public static class FixtureVO implements BaseVO<FixtureVO> {
	}

	// **** 模拟生成的 Service 三件套（DB 模板同步后的目标形态）**** //

	public interface FixtureService extends BaseService<FixtureEntity, FixtureDTO> {
	}

	static class FixtureServiceImpl implements FixtureService {
		@Override
		public BaseMapper<FixtureEntity> getMapper() {
			return null;
		}

		/** 生成模板仍产出该覆写：gaia default getLog() 保证其继续合法 */
		@Override
		public Log getLog() {
			return LogFactory.getLog(FixtureServiceImpl.class);
		}
	}

	// **** 模拟生成的 Controller（addBatch 模板调用点）**** //

	static class FixtureController extends BaseController<FixtureEntity, FixtureDTO, FixtureVO> {
		@Override
		protected BaseService<FixtureEntity, FixtureDTO> getService() {
			return new FixtureServiceImpl();
		}

		/** 生成模板的 super.addBatch 调用形态 */
		public R<Integer> batch(Collection<FixtureDTO> list) {
			return super.addBatch(list);
		}
	}

	@Test
	void 生成形态编译并可用() {
		FixtureController ctl = new FixtureController();
		assertSame(FixtureEntity.class, ctl.getClassE());
		assertSame(FixtureVO.class, ctl.getClassV());
		// 空集合批量走通（不触库）
		assertEquals(0, ctl.batch(List.of()).getData());
	}
}

package com.xwang.gaia.core.mybatisplus;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.xwang.gaia.core.mybatisplus.annotation.Relation;
import com.xwang.gaia.core.mybatisplus.injector.MySqlInjector;
import com.xwang.gaia.testfix.ScopeBaseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @Relation 注入层验证：
 * 1) ScopeBaseMapper 间接实现的 Mapper 也注入 ByScope 语句（识别需遍历接口链，等价 isAssignable）；
 * 2) 存量"函数式 columnStr"（IFNULL/表达式）注入期不抛异常——
 *    实测业务 6 线约 15 处此类写法今天正常启动运行，启动即抛属回归（源库 ledger Task 6 Ruling：校验降级 WARN）。
 */
class RelationValidationTest {

	// **** fixture：函数式 columnStr 实体（模拟业务存量写法）**** //

	@TableName("golden_func_rel")
	public static class FuncRelEntity {
		@TableId
		private Long id;
		private String name;

		/** 存量业务真实形态：IFNULL 表达式 columnStr + 非表字段承接 */
		@Relation(columnStr = "IFNULL(t1.cnt, 0) cnt",
				condition = "left join golden_func_stat t1 on t1.rel_id = t.id", wherePrefix = "t1")
		@TableField(exist = false)
		private Integer cnt;

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public Integer getCnt() {
			return cnt;
		}

		public void setCnt(Integer cnt) {
			this.cnt = cnt;
		}
	}

	public interface FuncRelMapper extends BaseMapper<FuncRelEntity> {
	}

	// **** fixture：ScopeBaseMapper 间接继承链 **** //

	@TableName("golden_scope")
	public static class ScopeEntity {
		@TableId
		private Long id;
		/** 普通列：无字段时 MP 不生成实体条件 el，WHERE 脚本渲染会 NPE（非被测行为，补桩） */
		private String name;
	}

	public interface MidScopeMapper extends ScopeBaseMapper<ScopeEntity> {
	}

	public interface LeafScopeMapper extends MidScopeMapper {
	}

	private static MybatisConfiguration inject(Class<?> mapperClass, Class<?> entityClass) {
		MybatisConfiguration configuration = new MybatisConfiguration();
		GlobalConfig globalConfig = GlobalConfigUtils.defaults();
		globalConfig.setBanner(false);
		GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
		assistant.setCurrentNamespace(mapperClass.getName());
		TableInfo table = TableInfoHelper.initTableInfo(assistant, entityClass);
		MySqlInjector injector = new MySqlInjector(ScopeBaseMapper.class.getName());
		List<AbstractMethod> methodList = injector.getMethodList(mapperClass, table);
		for (AbstractMethod method : methodList) {
			method.inject(assistant, mapperClass, entityClass, table);
		}
		return configuration;
	}

	@Test
	void 间接继承数据权限接口也注入ByScope语句() {
		MybatisConfiguration configuration = inject(LeafScopeMapper.class, ScopeEntity.class);
		// ByScope 两条语句按数据权限接口血统注入
		assertNotNull(configuration.getMappedStatement(
				LeafScopeMapper.class.getName() + ".selectListByScope", false));
		assertNotNull(configuration.getMappedStatement(
				LeafScopeMapper.class.getName() + ".selectPage", false));
	}

	@Test
	void 存量函数式columnStr注入不抛异常() {
		MybatisConfiguration configuration = assertDoesNotThrow(
				() -> inject(FuncRelMapper.class, FuncRelEntity.class));
		assertNotNull(configuration.getMappedStatement(
				FuncRelMapper.class.getName() + ".selectPage", false));
	}

	@Test
	void 直接继承数据权限接口行为不回归() {
		MybatisConfiguration configuration = inject(MidScopeMapper.class, ScopeEntity.class);
		assertNotNull(configuration.getMappedStatement(
				MidScopeMapper.class.getName() + ".selectPageByScope", false));
	}
}

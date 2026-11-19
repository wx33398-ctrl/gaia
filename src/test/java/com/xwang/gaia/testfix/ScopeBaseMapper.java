package com.xwang.gaia.testfix;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 测试替身：模拟宿主项目的数据权限 Mapper 基接口。
 * MySqlInjector 按全限定类名字符串匹配（构造参数 datascopeMapperClass），
 * 测试把本接口 FQN 传给注入器即可触发 SelectListByScope/SelectPageByScope 注入路径。
 */
public interface ScopeBaseMapper<T> extends BaseMapper<T> {
}

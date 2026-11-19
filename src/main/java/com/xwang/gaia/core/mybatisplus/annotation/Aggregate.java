package com.xwang.gaia.core.mybatisplus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 聚合属性
 * <p>
 * 生成形如 {@code ,(select count(1) ... where x = t.id) as 字段} 的关联子查询列。
 * <p>
 * <b>仅 {@code selectById} 生效</b>；{@code selectPage / selectList / selectCount / *ByScope} 一律不拼。
 * 原因：关联子查询按结果行执行，N 行结果就是 N 次子查询，列表/分页场景代价不可控；
 * 单条详情可接受。列表要聚合列请走自定义 Mapper SQL（一次 group by join）。
 *
 * @author xwang
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Aggregate {
    String value();
}


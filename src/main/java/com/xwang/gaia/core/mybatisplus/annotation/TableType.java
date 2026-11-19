package com.xwang.gaia.core.mybatisplus.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表类型标记注解，用于驱动缓存 TTL 策略。
 * CONFIG → 3600s，BUSINESS → 1s（默认）
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TableType {
    TableTypeEnum value() default TableTypeEnum.BUSINESS;
}


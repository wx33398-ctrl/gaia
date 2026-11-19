package com.xwang.gaia.core.mybatisplus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 支持mybaits-Plus 表关联
 * @author xwang
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Relation {
    String condition();
	String columnStr();
	String wherePrefix() default "";
	int type() default 0;
}


package com.xwang.gaia.core.base;

import net.dreamlu.mica.core.utils.BeanUtil;

import java.io.Serializable;

/**
 * 提供高性能的属性复制能力
 *
 * @author xwang
 * @param <T>
 */
public interface BaseVO<T extends BaseVO> extends Serializable {

	/**
	 * 将entity转化成VO，并回调用户自定义转换
	 *
	 * @param e
	 * @return
	 */
	default T of(Object e) {
		if (e == null) {
			return null;
		}
		T obj = (T) BeanUtil.copy(e, getClass());
		obj.afterPropertySet();
		return obj;
	}

	/**
	 * 属性复制后，提供回调操作
	 */
	default void afterPropertySet() {}
}

package com.xwang.gaia.core.exception;

import java.io.Serializable;

/**
 * 错误码封装
 */
public interface ExceptionStatus extends Serializable {
	/**
	 * 错误码
	 */
	int getCode();

	/**
	 * 错误信息
	 */
	String getMsg();
}

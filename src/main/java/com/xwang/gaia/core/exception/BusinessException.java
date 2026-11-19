package com.xwang.gaia.core.exception;

/**
 * 通用业务异常封装
 *
 * @author xwang
 */
public class BusinessException extends RuntimeException {

	private final ExceptionStatus status;

	public BusinessException(ExceptionStatus status) {
		super(status.getMsg(), null, false, true);
		this.status = status;
	}

	public BusinessException(ExceptionStatus status, String message) {
		super(message, null, false, true);
		this.status = status;
	}

	public ExceptionStatus getStatus() {
		return status;
	}

	public enum Status implements ExceptionStatus {
		DATA_INSERT_ERROR(10101, "新增数据失败"),
		DATA_UPDATE_ERROR(10102, "修改数据失败"),
		DATA_DELETE_ERROR(10103, "删除数据失败"),
		NOT_FOUND_RECORD(10104, "记录不存在"),
		BEAN_COPY_ERROR(10105, "对象拷贝异常"),
		NOT_FOUND_CLASS(10106, "类不存在"),
		NOT_FOUND_METHOD(10107, "方法不存在"),
		USERNAME_FOUND(10108, "用户名存在");

		private final int code;
		private final String msg;

		Status(int code, String msg) {
			this.code = code;
			this.msg = msg;
		}

		@Override
		public int getCode() {
			return this.code;
		}

		@Override
		public String getMsg() {
			return this.msg;
		}
	}
}

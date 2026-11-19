package com.xwang.gaia.core.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xwang.gaia.core.exception.ExceptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 响应信息主体
 *
 * @param <T>
 * @author xwang
 */
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class R<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	@Getter
	@Setter
	@JsonPropertyDescription(value = "调用成功返回0，调用失败返回错误码")
	private int code;

	@Getter
	@Setter
	@JsonPropertyDescription(value = "返回具体的信息")
	private String msg;

	@Getter
	@Setter
	private T data;

	@JsonIgnore
	public boolean isSuccess() {
		return code == 0;
	}

	// 不要改回 static 常量：R 带公开 @Setter，共享实例被某处 setMsg/setData 改到会污染全 JVM
	public static R success() {
		return new R().setCode(0).setMsg("success");
	}

	public static <T> R<T> success(T data) {
		if (data == null) {
			return new R().setCode(0).setMsg("success");
		} else {
			return new R().setCode(0).setMsg("success").setData(data);
		}
	}

	/**
	 * 自动处理分页，提供entity转换function
	 */
	public static <T, S> R success(IPage<T> page, Function<? super T, ? extends S> func) {
		R result = new R().setCode(0).setMsg("success");
		if (page == null) {
			result.setData(null);
			return result;
		}
		Data data = new Data(page.getTotal(), page.getPages(), page.getSize(), page.getCurrent());
		if (null != page.getRecords() && page.getRecords().size() > 0) {
			if (func != null) {
				data.setRecords(page.getRecords().stream().map(func).collect(Collectors.toList()));
			}
		}
		result.setData(data);
		return result;
	}

	/**
	 * 自动处理分页，提供entity转换function
	 */
	public static <T, S> R success(IPage<T> page, Function<? super T, ? extends S> func, Consumer<S> func2) {
		R result = new R().setCode(0).setMsg("success");
		if (page == null) {
			result.setData(null);
			return result;
		}
		Data data = new Data(page.getTotal(), page.getPages(), page.getSize(), page.getCurrent());
		if (null != page.getRecords() && page.getRecords().size() > 0) {
			if (func != null) {
				List<S> recordList = page.getRecords().stream().map(func).collect(Collectors.toList());
				if (func2 != null) {
					recordList.forEach(func2);
				}
				data.setRecords(recordList);
			}
		}
		result.setData(data);
		return result;
	}

	/**
	 * 自动处理分页，提供entity转换function
	 */
	public static <T, S> R success(List<T> list, Function<? super T, ? extends S> func) {
		R result = new R().setCode(0).setMsg("success");
		if (list == null || list.size() == 0) {
			result.setData(null);
			return result;
		}
		List<S> newList = list.stream().map(func).collect(Collectors.toList());
		result.setData(newList);
		return result;
	}

	/**
	 * 自动处理分页，提供entity转换function
	 */
	public static <T, S> R success(List<T> list, Function<? super T, ? extends S> func, Consumer<S> func2) {
		R result = new R().setCode(0).setMsg("success");
		if (list == null || list.size() == 0) {
			result.setData(null);
			return result;
		}
		List<S> newList = list.stream().map(func).collect(Collectors.toList());
		if (func2 != null) {
			newList.forEach(func2);
		}
		result.setData(newList);
		return result;
	}

	public static R failure(ExceptionStatus resultStatus) {
		R result = new R<>();
		result.setCode(resultStatus.getCode());
		result.setMsg(resultStatus.getMsg());
		return result;
	}

	public static R failure(ExceptionStatus resultStatus, String message) {
		R result = new R<>();
		result.setCode(resultStatus.getCode());
		result.setMsg(message != null ? message : resultStatus.getMsg());
		return result;
	}

	public static R<Throwable> failure(ExceptionStatus resultStatus, Throwable e) {
		R<Throwable> result = new R<>();
		result.setCode(resultStatus.getCode());
		result.setMsg(resultStatus.getMsg());
		result.setData(e);
		return result;
	}

	@lombok.Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class Data implements Serializable {
		private static final long serialVersionUID = 1L;

		public Data(long total, long pages, long size, long current) {
			this.total = total;
			this.pages = pages;
			this.size = size;
			this.current = current;
		}

		private long total;
		private long pages;
		private long size;
		private long current;
		private List<?> records;
	}
}

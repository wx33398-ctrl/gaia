package com.xwang.gaia.core.base;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO父类，提供基本的分页参数
 * @author xwang
 * @param <D>
 */
@ToString
public abstract class BaseDTO<D extends BaseDTO> {
	/**
	 * 分页参数钳制上限（设计文档批准：size 超过 1000 一律按 1000 处理）
	 */
	public static final long MAX_PAGE_SIZE = 1000L;
	/**
	 * 分页每页数
	 */
	private Long size = 10L;
	/**
	 * 每页数
	 */
	private Long current = 1L;
	/**
	 * 按照升序排序字段，支持多字段用逗号分隔
	 */
	private String ascs;
	/**
	 * 按照降序排序字段，支持多字段用逗号分隔
	 */
	private String descs;

	/**
	 * 数据导出最大限制
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long maxLimit = 0L;

	/**
	 * 排序字段信息
	 */
	private List<OrderItem> orders = new ArrayList<>();

	/**
	 * 自动优化 COUNT SQL
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Boolean optimizeCountSql = true;
	/**
	 * 是否进行 count 查询
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Boolean isSearchCount = true;

	public D setSize(Long size) {
		this.size = size;
		return (D) this;
	}

	public D setCurrent(Long current) {
		this.current = current;
		return (D) this;
	}

	public D setAscs(String ascs) {
		this.ascs = ascs;
		return (D) this;
	}

	public D setDescs(String descs) {
		this.descs = descs;
		return (D) this;
	}

	public D setMaxLimit(Long maxLimit) {
		this.maxLimit = maxLimit;
		return (D) this;
	}

	public D setOrders(List<OrderItem> orders) {
		this.orders = orders;
		return (D) this;
	}

	public D setOptimizeCountSql(boolean optimizeCountSql) {
		this.optimizeCountSql = optimizeCountSql;
		return (D) this;
	}

	public D setSearchCount(boolean searchCount) {
		this.isSearchCount = searchCount;
		return (D) this;
	}

//	public D setIds(List<Serializable> ids) {
//		this.ids = ids;
//		return (D) this;
//	}

	public Long getSize() {
		return size;
	}

	public Long getCurrent() {
		return current;
	}

	public String getAscs() {
		return ascs;
	}

	public String getDescs() {
		return descs;
	}

	public Long getMaxLimit() {
		return maxLimit;
	}

	public List<OrderItem> getOrders() {
		return orders;
	}

	public boolean isOptimizeCountSql() {
		return !Boolean.FALSE.equals(optimizeCountSql);
	}

	@JsonIgnore
	public boolean isSearchCount() {
		return !Boolean.FALSE.equals(isSearchCount);
	}

//	public List<Serializable> getIds() {
//		return ids;
//	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public IPage getPage(){
		// 设计文档批准：null 走默认值不 NPE；current<1 钳到 1；size>1000 钳到 1000
		// size<1 视为未指定走默认 10，不透传：MP 的「size<0 不分页」要求 maxLimit 为 null，
		// 而下面 maxLimit 只在 >0 时下发，透传 -1 会拼出 `LIMIT -1` → MySQL 1064。要全量请显式设 maxLimit>0。
		long cur = current == null || current < 1 ? 1L : current;
		long sz = size == null || size < 1 ? 10L : Math.min(size, MAX_PAGE_SIZE);
		Page page = new Page(cur, sz, isSearchCount());
		page.setOptimizeCountSql(isOptimizeCountSql());
		// maxLimit 仅在 >0 时下发：0L 会让 MP 用 page.maxLimit() 覆盖掉宿主在分页拦截器上配的全局 maxLimit，
		// 也会让「size<0 不分页」的短路失效。null 才是 MP 的「不覆盖」语义。
		page.setMaxLimit(maxLimit != null && maxLimit > 0 ? maxLimit : null);
		page.setOrders(orders);
		return page;
	}
}

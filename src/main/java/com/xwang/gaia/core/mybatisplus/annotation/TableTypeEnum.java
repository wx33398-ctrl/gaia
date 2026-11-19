package com.xwang.gaia.core.mybatisplus.annotation;

/**
 * 表类型枚举，用于缓存 TTL 策略区分
 */
public enum TableTypeEnum {
    /** 配置类 — 人工后台管理，缓存 TTL 3600s */
    CONFIG,
    /** 业务动态类 — 业务自动生成或用户操作，缓存 TTL 1s */
    BUSINESS
}


package com.miaofan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解, 基于 Redis 令牌桶实现
 * <p>
 * 标在 Controller 方法上即可生效, 限流维度默认为"接口 + 当前登录用户"
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 桶容量, 也就是允许的瞬时突发请求数
     */
    int capacity() default 20;

    /**
     * 每秒补充的令牌数, 也就是长期允许的 QPS
     */
    int rate() default 10;
}

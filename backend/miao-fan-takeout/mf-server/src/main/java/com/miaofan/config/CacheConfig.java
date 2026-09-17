package com.miaofan.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存异常处理
 * <p>
 * 背景: 项目里的缓存用 JDK 序列化, 值里会记录类的全名。
 * 只要缓存里存在类名与当前代码不一致的旧数据(比如重构前的包名),
 * 新程序读出来就会抛 ClassNotFoundException, 整个接口 500。
 * <p>
 * 这里把缓存读写异常降级处理: 读失败当成"未命中"回源数据库, 写失败只记日志。
 * 缓存是加速手段, 不该因为它不可用就让业务接口挂掉。
 */
@Configuration
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("读取缓存失败, 按未命中处理(将回源数据库): cache={}, key={}, 原因={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("写入缓存失败, 已忽略: cache={}, key={}, 原因={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("清理缓存失败, 已忽略: cache={}, key={}", cache.getName(), key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("清空缓存失败, 已忽略: cache={}", cache.getName());
            }
        };
    }
}

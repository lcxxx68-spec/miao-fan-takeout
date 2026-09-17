package com.miaofan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Redis Lua 脚本配置
 * <p>
 * 脚本在应用启动时加载一次, 之后由 Spring Data Redis 缓存并复用(EVALSHA),
 * 不需要每次请求都把脚本内容传给 Redis
 */
@Configuration
public class RedisLuaConfig {

    /**
     * 抢购库存原子扣减脚本, 返回结果码
     */
    @Bean
    public DefaultRedisScript<Long> seckillStockScript() {
        return buildScript("lua/seckill_stock.lua");
    }

    /**
     * 令牌桶限流脚本, 返回是否放行
     */
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        return buildScript("lua/rate_limit.lua");
    }

    private DefaultRedisScript<Long> buildScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        //显式用 UTF-8 读取, 否则脚本里的中文注释在非 UTF-8 默认编码的机器上会乱码
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            script.setScriptText(StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("加载 Lua 脚本失败: " + path, e);
        }
        script.setResultType(Long.class);
        return script;
    }
}

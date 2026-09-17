package com.miaofan.interceptor;

import com.miaofan.annotation.RateLimit;
import com.miaofan.constant.MessageConstant;
import com.miaofan.constant.SeckillConstant;
import com.miaofan.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

/**
 * 限流拦截器
 * <p>
 * 只对标注了 @RateLimit 的方法生效, 未标注的接口不做任何处理
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DefaultRedisScript<Long> rateLimitScript;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        //限流维度: 接口 + 用户(未登录时退化成按 IP)
        Long userId = BaseContext.getCurrentId();
        String dimension = userId != null ? String.valueOf(userId) : request.getRemoteAddr();
        String key = SeckillConstant.RATE_LIMIT_PREFIX + handlerMethod.getMethod().getName() + ":" + dimension;

        Long allowed = stringRedisTemplate.execute(rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(rateLimit.capacity()),
                String.valueOf(rateLimit.rate()),
                String.valueOf(System.currentTimeMillis()),
                "1");

        if (allowed != null && allowed == 1L) {
            return true;
        }

        log.warn("请求被限流: {}", key);
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"" + MessageConstant.SECKILL_RATE_LIMIT + "\",\"data\":null}");
        response.getWriter().flush();
        return false;
    }
}

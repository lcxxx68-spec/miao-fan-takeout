package com.miaofan.exception;

/**
 * 限时抢购业务异常
 * <p>
 * 继承 BaseException 后会被 GlobalExceptionHandler 统一捕获并包装成 Result 返回
 */
public class SeckillBusinessException extends BaseException {

    public SeckillBusinessException(String msg) {
        super(msg);
    }
}

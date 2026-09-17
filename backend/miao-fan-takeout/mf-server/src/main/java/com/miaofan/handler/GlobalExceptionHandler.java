package com.miaofan.handler;

import com.miaofan.constant.MessageConstant;
import com.miaofan.exception.BaseException;
import com.miaofan.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }


    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        //Duplicate entry 'zhangsan' for key 'employee.idx_username'
        log.error("SQL异常信息{}", ex.getMessage());
        String message = ex.getMessage();
        if (message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String username=split[2];
            String msg=username+ MessageConstant.ALREADY_EXITS;
            return Result.error(msg);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }

    /**
     * 处理请求体解析失败, 例如时间格式不是 yyyy-MM-dd HH:mm、JSON 结构不合法
     * <p>
     * 没有这个处理器时, 这类错误会返回 Spring 默认的错误结构,
     * 前端拿到的既不是 Result 也没有统一提示, 排查起来很别扭
     */
    @ExceptionHandler
    public Result exceptionHandler(HttpMessageNotReadableException ex){
        log.error("请求参数解析失败: {}", ex.getMessage());
        return Result.error(MessageConstant.PARAM_FORMAT_ERROR);
    }
}

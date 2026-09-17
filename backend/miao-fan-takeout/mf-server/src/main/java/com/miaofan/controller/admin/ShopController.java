package com.miaofan.controller.admin;

import com.miaofan.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController("adminShopController" )
@RequestMapping("/admin/shop")
@Api("店铺相关接口")
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;

    public static final String KEY="SHOP_STATUS";

    @PutMapping("/{status}")
    @ApiOperation("设置店铺当前状态")
    public Result setShopStatus(@PathVariable Integer status) {
        log.info("设置当前店铺状态为:{}", status==1?"营业中":"打样中");
        redisTemplate.opsForValue().set(KEY, status);
        return Result.success();
    }

    @GetMapping("/status")
    @ApiOperation("获取店铺当前状态")
    public Result<Integer> getShopStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        log.info("店铺当前状态为:{}",status==1?"营业中":"打样中");
        return Result.success(status);
    }
}

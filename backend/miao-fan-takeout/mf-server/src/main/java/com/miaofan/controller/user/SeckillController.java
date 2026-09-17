package com.miaofan.controller.user;

import com.miaofan.annotation.RateLimit;
import com.miaofan.dto.SeckillDTO;
import com.miaofan.result.Result;
import com.miaofan.service.SeckillOrderService;
import com.miaofan.vo.SeckillActivityVO;
import com.miaofan.vo.SeckillResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端限时抢购
 */
@RestController
@RequestMapping("/user/seckill")
@Api(tags = "用户端限时抢购接口")
@Slf4j
public class SeckillController {

    @Autowired
    private SeckillOrderService seckillOrderService;

    /**
     * 进行中的抢购活动
     */
    @GetMapping("/list")
    @ApiOperation("查询进行中的抢购活动")
    public Result<List<SeckillActivityVO>> list() {
        return Result.success(seckillOrderService.listOnline());
    }

    /**
     * 参与抢购
     * <p>
     * 限流参数: 桶容量 5(允许 5 次突发), 每秒补充 1 个令牌
     */
    @PostMapping("/{activityId}")
    @RateLimit(capacity = 5, rate = 1)
    @ApiOperation("参与限时抢购")
    public Result<SeckillResultVO> seckill(@PathVariable Long activityId, @RequestBody SeckillDTO seckillDTO) {
        log.info("参与抢购: activityId={}, dto={}", activityId, seckillDTO);
        return Result.success(seckillOrderService.seckill(activityId, seckillDTO));
    }

    /**
     * 查询抢购结果
     */
    @GetMapping("/result/{activityId}")
    @ApiOperation("查询抢购结果")
    public Result<SeckillResultVO> result(@PathVariable Long activityId) {
        return Result.success(seckillOrderService.queryResult(activityId));
    }
}

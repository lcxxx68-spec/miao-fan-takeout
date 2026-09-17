package com.miaofan.controller.admin;

import com.miaofan.dto.SeckillActivityDTO;
import com.miaofan.dto.SeckillActivityPageQueryDTO;
import com.miaofan.result.PageResult;
import com.miaofan.result.Result;
import com.miaofan.service.SeckillActivityService;
import com.miaofan.vo.SeckillActivityVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 限时抢购管理
 */
@RestController
@RequestMapping("/admin/seckill")
@Api(tags = "限时抢购管理接口")
@Slf4j
public class SeckillActivityController {

    @Autowired
    private SeckillActivityService seckillActivityService;

    /**
     * 新增抢购活动
     */
    @PostMapping
    @ApiOperation("新增抢购活动")
    public Result<String> save(@RequestBody SeckillActivityDTO seckillActivityDTO) {
        log.info("新增抢购活动: {}", seckillActivityDTO);
        seckillActivityService.save(seckillActivityDTO);
        return Result.success();
    }

    /**
     * 活动分页查询
     */
    @GetMapping("/page")
    @ApiOperation("活动分页查询")
    public Result<PageResult> page(SeckillActivityPageQueryDTO seckillActivityPageQueryDTO) {
        log.info("活动分页查询: {}", seckillActivityPageQueryDTO);
        return Result.success(seckillActivityService.pageQuery(seckillActivityPageQueryDTO));
    }

    /**
     * 修改活动
     */
    @PutMapping
    @ApiOperation("修改抢购活动")
    public Result<String> update(@RequestBody SeckillActivityDTO seckillActivityDTO) {
        log.info("修改抢购活动: {}", seckillActivityDTO);
        seckillActivityService.update(seckillActivityDTO);
        return Result.success();
    }

    /**
     * 上架、停售活动
     */
    @PostMapping("/status/{status}")
    @ApiOperation("上架停售活动")
    public Result<String> startOrStop(@PathVariable("status") Integer status, Long id) {
        log.info("活动状态变更: status={}, id={}", status, id);
        seckillActivityService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 活动详情
     */
    @GetMapping("/{id}")
    @ApiOperation("查询活动详情")
    public Result<SeckillActivityVO> getById(@PathVariable Long id) {
        log.info("查询活动详情: id={}", id);
        return Result.success(seckillActivityService.getById(id));
    }
}

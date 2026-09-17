package com.miaofan.service;

import com.miaofan.dto.SeckillActivityDTO;
import com.miaofan.dto.SeckillActivityPageQueryDTO;
import com.miaofan.result.PageResult;
import com.miaofan.vo.SeckillActivityVO;

import java.util.List;

/**
 * 限时抢购活动业务接口
 */
public interface SeckillActivityService {

    /**
     * 管理端分页查询
     */
    PageResult pageQuery(SeckillActivityPageQueryDTO seckillActivityPageQueryDTO);

    /**
     * 新增活动
     */
    void save(SeckillActivityDTO seckillActivityDTO);

    /**
     * 修改活动
     */
    void update(SeckillActivityDTO seckillActivityDTO);

    /**
     * 上架 / 停售
     *
     * @param status 1上架 0停售
     */
    void startOrStop(Integer status, Long id);

    /**
     * 查询活动详情
     */
    SeckillActivityVO getById(Long id);

    /**
     * 用户端可见的活动列表(已上架且在活动时间内)
     */
    List<SeckillActivityVO> listOnline();
}

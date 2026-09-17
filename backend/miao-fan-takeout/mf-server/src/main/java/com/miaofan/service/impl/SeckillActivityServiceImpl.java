package com.miaofan.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.miaofan.constant.MessageConstant;
import com.miaofan.constant.SeckillConstant;
import com.miaofan.dto.SeckillActivityDTO;
import com.miaofan.dto.SeckillActivityPageQueryDTO;
import com.miaofan.entity.SeckillActivity;
import com.miaofan.exception.SeckillBusinessException;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.result.PageResult;
import com.miaofan.service.SeckillActivityService;
import com.miaofan.service.SeckillCacheService;
import com.miaofan.vo.SeckillActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class SeckillActivityServiceImpl implements SeckillActivityService {

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Override
    public PageResult pageQuery(SeckillActivityPageQueryDTO seckillActivityPageQueryDTO) {
        PageHelper.startPage(seckillActivityPageQueryDTO.getPage(), seckillActivityPageQueryDTO.getPageSize());
        Page<SeckillActivityVO> page = seckillActivityMapper.pageQuery(seckillActivityPageQueryDTO);

        // 把 Redis 里的剩余库存一起返回, 便于运营直观看到"数据库已售"和"Redis 预扣"两个视角
        for (SeckillActivityVO vo : page.getResult()) {
            vo.setRemainStock(seckillCacheService.getStock(vo.getId()));
        }
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    @Transactional
    public void save(SeckillActivityDTO seckillActivityDTO) {
        checkParam(seckillActivityDTO);

        SeckillActivity activity = new SeckillActivity();
        BeanUtils.copyProperties(seckillActivityDTO, activity);
        // 新活动的已售数量从 0 开始, 每人限购未填时默认 1
        activity.setSoldStock(0);
        if (activity.getPerUserLimit() == null || activity.getPerUserLimit() < 1) {
            activity.setPerUserLimit(1);
        }
        if (activity.getStatus() == null) {
            activity.setStatus(SeckillConstant.ACTIVITY_OFFLINE);
        }

        seckillActivityMapper.insert(activity);

        // 新增时如果直接就是上架状态, 立刻预热
        if (SeckillConstant.ACTIVITY_ONLINE.equals(activity.getStatus())) {
            seckillCacheService.warmUp(activity.getId());
        }
    }

    @Override
    @Transactional
    public void update(SeckillActivityDTO seckillActivityDTO) {
        if (seckillActivityDTO.getId() == null) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_NOT_FOUND);
        }

        SeckillActivity activity = new SeckillActivity();
        BeanUtils.copyProperties(seckillActivityDTO, activity);
        seckillActivityMapper.update(activity);

        // 活动信息变了, 缓存必须跟着变, 否则用户看到的还是旧价格、旧时间
        seckillCacheService.refresh(activity.getId());
    }

    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        SeckillActivityVO activity = seckillActivityMapper.getById(id);
        if (activity == null) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_NOT_FOUND);
        }

        if (SeckillConstant.ACTIVITY_ONLINE.equals(status)) {
            // 上架前做两项校验: 活动是否已经过期、库存是否还能卖
            if (activity.getEndTime().isBefore(LocalDateTime.now())) {
                throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_ENDED_ERROR);
            }
            if (activity.getSoldStock() >= activity.getTotalStock()) {
                throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_STATUS_ERROR);
            }
        }

        SeckillActivity updateActivity = SeckillActivity.builder()
                .id(id)
                .status(status)
                .build();
        seckillActivityMapper.update(updateActivity);

        if (SeckillConstant.ACTIVITY_ONLINE.equals(status)) {
            seckillCacheService.warmUp(id);
        } else {
            seckillCacheService.clear(id);
        }
        log.info("活动状态变更: id={}, status={}", id, status);
    }

    @Override
    public SeckillActivityVO getById(Long id) {
        SeckillActivityVO activity = seckillCacheService.getActivity(id);
        if (activity != null) {
            activity.setRemainStock(seckillCacheService.getStock(id));
        }
        return activity;
    }

    @Override
    public List<SeckillActivityVO> listOnline() {
        List<SeckillActivityVO> list = seckillActivityMapper.listOnline();
        for (SeckillActivityVO vo : list) {
            vo.setRemainStock(seckillCacheService.getStock(vo.getId()));
        }
        return list;
    }

    /**
     * 活动参数校验
     */
    private void checkParam(SeckillActivityDTO seckillActivityDTO) {
        if (!StringUtils.hasText(seckillActivityDTO.getName())
                || seckillActivityDTO.getDishId() == null
                || seckillActivityDTO.getStartTime() == null
                || seckillActivityDTO.getEndTime() == null) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_PARAM_ERROR);
        }
        if (!seckillActivityDTO.getStartTime().isBefore(seckillActivityDTO.getEndTime())) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_TIME_ERROR);
        }
        if (seckillActivityDTO.getSeckillPrice() == null
                || seckillActivityDTO.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_PRICE_ERROR);
        }
        if (seckillActivityDTO.getTotalStock() == null || seckillActivityDTO.getTotalStock() <= 0) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_STOCK_ERROR);
        }
    }
}

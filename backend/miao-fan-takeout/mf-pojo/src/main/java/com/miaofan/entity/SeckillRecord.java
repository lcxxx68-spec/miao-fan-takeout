package com.miaofan.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 限时抢购流水
 * <p>
 * 表上有 (activity_id, user_id) 唯一索引, 是"一人一单"的最后一道防线
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long activityId;

    private Long userId;

    //抢购成功后写入的订单id
    private Long orderId;

    //0排队中 1抢购成功 2抢购失败
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

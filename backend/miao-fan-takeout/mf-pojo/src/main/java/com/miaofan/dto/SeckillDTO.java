package com.miaofan.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户端参与抢购传递的数据
 */
@Data
public class SeckillDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    //收货地址id, 抢购成功后按该地址生成订单
    private Long addressBookId;

    //订单备注
    private String remark;
}

package com.miaofan.mapper;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.entity.SeckillRecord;
import com.miaofan.vo.SeckillActivityVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 限时抢购 Mapper 测试
 * <p>
 * 运行前请确认已执行 db/seckill.sql 建表并导入示例活动
 */
// 使用随机端口启动真实 Web 容器: WebSocket 的 ServerEndpointExporter 在默认的 MOCK 环境下拿不到 ServerContainer
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillActivityMapperTest {

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillRecordMapper seckillRecordMapper;

    /**
     * 活动能查出来, 并且关联出了菜品名称与原价
     */
    @Test
    void shouldQueryActivityWithDishInfo() {
        List<SeckillActivityVO> list = seckillActivityMapper.listOnline();
        assertFalse(list.isEmpty(), "示例活动应至少有一条已上架且处于活动时间内");

        SeckillActivityVO first = list.get(0);
        assertNotNull(first.getDishName(), "活动应关联出菜品名称");
        assertNotNull(first.getOriginalPrice(), "活动应关联出菜品原价");
        System.out.println("查询到活动: " + first);
    }

    /**
     * 扣减与回补是一对逆操作, 执行后库存应回到原值(不污染数据)
     */
    @Test
    void deductAndRecoverStockShouldKeepBalance() {
        SeckillActivityVO activity = seckillActivityMapper.listOnline().get(0);
        Long activityId = activity.getId();
        Integer before = seckillActivityMapper.getById(activityId).getSoldStock();

        assertEquals(1, seckillActivityMapper.deductStock(activityId), "库存充足时应扣减成功");
        assertEquals(1, seckillActivityMapper.recoverStock(activityId), "库存回补应成功");
        assertEquals(before, seckillActivityMapper.getById(activityId).getSoldStock(),
                "扣减后回补, 已售数量应回到原值");
    }

    /**
     * 唯一索引是"一人一单"的最后一道防线, 这里直接验证数据库真的会拦截重复抢购
     */
    @Test
    void uniqueIndexShouldBlockDuplicatePurchase() {
        Long activityId = 999999L;
        Long userId = 999999L;
        seckillRecordMapper.deleteByActivityIdAndUserId(activityId, userId);
        try {
            SeckillRecord record = SeckillRecord.builder()
                    .activityId(activityId)
                    .userId(userId)
                    .status(SeckillConstant.RECORD_QUEUING)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            seckillRecordMapper.insert(record);
            assertNotNull(record.getId(), "插入后应回填自增主键");

            assertThrows(DuplicateKeyException.class, () -> seckillRecordMapper.insert(record),
                    "同一用户重复抢购同一活动, 应被唯一索引拦截");
        } finally {
            seckillRecordMapper.deleteByActivityIdAndUserId(activityId, userId);
        }
    }
}

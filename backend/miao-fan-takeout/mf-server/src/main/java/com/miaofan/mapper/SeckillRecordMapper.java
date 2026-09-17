package com.miaofan.mapper;

import com.miaofan.entity.SeckillRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeckillRecordMapper {

    /**
     * 写入抢购流水
     * <p>
     * 表上有 (activity_id, user_id) 唯一索引: 同一用户重复抢购同一活动时,
     * 这里会抛出 DuplicateKeyException, 这正是"一人一单"的兜底手段
     */
    void insert(SeckillRecord record);

    /**
     * 动态修改流水(回写订单id、更新状态)
     */
    void update(SeckillRecord record);

    /**
     * 根据活动id与用户id查询流水
     */
    @Select("select * from seckill_record where activity_id = #{activityId} and user_id = #{userId}")
    SeckillRecord getByActivityIdAndUserId(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /**
     * 查询某活动下指定状态的流水, 对账任务使用
     */
    @Select("select * from seckill_record where activity_id = #{activityId} and status = #{status}")
    List<SeckillRecord> listByActivityIdAndStatus(@Param("activityId") Long activityId,
                                                  @Param("status") Integer status);

    /**
     * 删除流水, 仅用于测试数据清理
     */
    @Delete("delete from seckill_record where activity_id = #{activityId} and user_id = #{userId}")
    void deleteByActivityIdAndUserId(@Param("activityId") Long activityId, @Param("userId") Long userId);
}

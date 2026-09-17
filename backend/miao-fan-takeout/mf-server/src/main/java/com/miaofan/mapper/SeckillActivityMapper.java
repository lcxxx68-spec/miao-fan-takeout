package com.miaofan.mapper;

import com.github.pagehelper.Page;
import com.miaofan.annotation.AutoFill;
import com.miaofan.dto.SeckillActivityPageQueryDTO;
import com.miaofan.entity.SeckillActivity;
import com.miaofan.enumeration.OperationType;
import com.miaofan.vo.SeckillActivityVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SeckillActivityMapper {

    /**
     * 新增活动
     * <p>
     * 公共字段(创建时间/创建人/更新时间/更新人)由 AutoFillAspect 切面自动填充
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(SeckillActivity activity);

    /**
     * 动态修改活动
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(SeckillActivity activity);

    /**
     * 管理端活动分页查询(关联菜品名称与图片)
     */
    Page<SeckillActivityVO> pageQuery(SeckillActivityPageQueryDTO seckillActivityPageQueryDTO);

    /**
     * 根据主键查询活动详情
     */
    SeckillActivityVO getById(Long id);

    /**
     * 查询用户端可见的活动: 已上架 + 当前时间在活动时间区间内
     */
    List<SeckillActivityVO> listOnline();

    /**
     * 按状态查询活动, 对账任务使用
     */
    @Select("select * from seckill_activity where status = #{status}")
    List<SeckillActivity> listByStatus(Integer status);

    /**
     * 乐观扣减库存: 只有 已售数量 < 总库存 时才会更新成功
     * <p>
     * 这是数据库层面的防超卖兜底, 返回受影响行数, 0 表示已售罄
     */
    @Update("update seckill_activity set sold_stock = sold_stock + 1 where id = #{id} and sold_stock < total_stock")
    int deductStock(Long id);

    /**
     * 库存回补: 订单超时取消或下单失败时调用
     */
    @Update("update seckill_activity set sold_stock = sold_stock - 1 where id = #{id} and sold_stock > 0")
    int recoverStock(Long id);

    /**
     * 删除活动, 仅用于测试数据清理与后台维护
     */
    @Update("delete from seckill_activity where id = #{id}")
    void deleteById(Long id);
}

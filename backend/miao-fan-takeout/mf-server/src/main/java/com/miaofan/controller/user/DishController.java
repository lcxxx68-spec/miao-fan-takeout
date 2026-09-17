package com.miaofan.controller.user;

import com.miaofan.constant.StatusConstant;
import com.miaofan.entity.Dish;
import com.miaofan.result.Result;
import com.miaofan.service.DishService;
import com.miaofan.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {

        //构造redis中的key，规则：dish_分类id
        String key = "dish_" + categoryId;

        // 查询 redis 中是否存在菜品数据
        // 注意: 缓存用 JDK 序列化, 值里带类名。如果缓存里是旧版本程序写的数据,
        // 反序列化会抛 ClassNotFoundException。这里降级为"未命中"并删掉坏缓存, 避免整个接口 500
        List<DishVO> list = null;
        try {
            list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("菜品缓存读取失败, 已删除坏缓存并回源数据库: key={}, 原因={}", key, e.getMessage());
            redisTemplate.delete(key);
        }
        if(list != null && list.size() > 0){
            //如果存在，直接返回，无须查询数据库
            return Result.success(list);
        }

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        //如果不存在，查询数据库，将查询到的数据放入redis中
        list = dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key, list);

        return Result.success(list);
    }

}

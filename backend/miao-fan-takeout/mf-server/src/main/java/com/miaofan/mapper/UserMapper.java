package com.miaofan.mapper;

import com.miaofan.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    @Select("select * from user where openid=#{openid}")
    User getByOpenid(String openid);

    void insert(User user);

    @Select("select *from user where id=#{id}")
    User getById(Long userId);

    Integer countUsers(@Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime);
}

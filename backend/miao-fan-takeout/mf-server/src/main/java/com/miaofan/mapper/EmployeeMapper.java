package com.miaofan.mapper;

import com.github.pagehelper.Page;
import com.miaofan.annotation.AutoFill;
import com.miaofan.dto.EmployeePageQueryDTO;
import com.miaofan.entity.Employee;
import com.miaofan.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    //新增员工
    @AutoFill(value = OperationType.INSERT)
    @Insert("insert into employee (name, username, password, phone, sex, id_number, " +
            "create_time, update_time, create_user, update_user) VALUES " +
            "(#{name},#{username},#{password},#{phone},#{sex},#{idNumber}," +
            "#{createTime},#{updateTime},#{createUser},#{updateUser})")
    void save(Employee employee);

    //分页查询
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    //启用禁用员工(并到更新员工大类里)
    @AutoFill(value = OperationType.UPDATE)
    void update(Employee employee);

    //根据id查询员工信息
    @Select("select * from employee where id=#{id}")
    Employee selectById(Long id);
}

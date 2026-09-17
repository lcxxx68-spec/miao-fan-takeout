package com.miaofan.service;

import com.miaofan.dto.EmployeeDTO;
import com.miaofan.dto.EmployeeLoginDTO;
import com.miaofan.dto.EmployeePageQueryDTO;
import com.miaofan.entity.Employee;
import com.miaofan.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    //新增员工
    void save(EmployeeDTO employeeDTO);

    //分页查询
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    void startOrStop(Integer status, Long id);

    //根据id查询员工
    Employee selectById(Long id);

    //修改员工信息
    void update(EmployeeDTO employeeDTO);
}

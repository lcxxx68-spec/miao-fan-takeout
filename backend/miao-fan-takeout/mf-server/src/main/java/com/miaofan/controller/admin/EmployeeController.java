package com.miaofan.controller.admin;

import com.github.pagehelper.Page;
import com.miaofan.constant.JwtClaimsConstant;
import com.miaofan.dto.EmployeeDTO;
import com.miaofan.dto.EmployeeLoginDTO;
import com.miaofan.dto.EmployeePageQueryDTO;
import com.miaofan.entity.Employee;
import com.miaofan.properties.JwtProperties;
import com.miaofan.result.PageResult;
import com.miaofan.result.Result;
import com.miaofan.service.EmployeeService;
import com.miaofan.utils.JwtUtil;
import com.miaofan.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Api(tags = "员工相关接口")
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("员工登录")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @ApiOperation("员工退出")
    public Result<String> logout() {
        return Result.success();
    }

    //新增员工
    //补充返回值
    @PostMapping
    @ApiOperation("新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO) {
        log.info("<新增员工>{}", employeeDTO);
        employeeService.save(employeeDTO);
        return Result.success();
    }


    //分页查询
    @GetMapping("/page")
    @ApiOperation("分页查询")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO) {
        log.info("当前分页查询的信息为:{}", employeePageQueryDTO);
        PageResult pageResult=employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    //启用禁用员工
    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用员工")
    public Result startOrStop(@PathVariable Integer status,Long id) {
        log.info("修改员工状态...");
        employeeService.startOrStop(status,id);
        return Result.success();
    }

    //根据id查询员工信息
    @GetMapping("/{id}")
    @ApiOperation("根据id查询员工")
    public Result<Employee> selectById(@PathVariable Long id){
        Employee employee=employeeService.selectById(id);
        return Result.success(employee);
    }

    //修改员工信息
    @PutMapping
    @ApiOperation("修改员工信息")
    public Result update(@RequestBody EmployeeDTO employeeDTO) {
        employeeService.update(employeeDTO);
        return Result.success();
    }
}

package com.miaofan.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "员工登录返回的数据格式")
public class EmployeeLoginVO implements Serializable {

    @ApiModelProperty("主键值")
    private Long id;

    @ApiModelProperty("用户名")
    private String userName;

    @ApiModelProperty("姓名")
    private String name;

    @ApiModelProperty("jwt令牌")
    private String token;

    public static EmployeeLoginVOBuilder builder() {
        return new EmployeeLoginVOBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public static class EmployeeLoginVOBuilder {
        private Long id;
        private String userName;
        private String name;
        private String token;

        EmployeeLoginVOBuilder() {
        }

        public EmployeeLoginVOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public EmployeeLoginVOBuilder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public EmployeeLoginVOBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EmployeeLoginVOBuilder token(String token) {
            this.token = token;
            return this;
        }

        public EmployeeLoginVO build() {
            EmployeeLoginVO employeeLoginVO = new EmployeeLoginVO();
            employeeLoginVO.setId(id);
            employeeLoginVO.setUserName(userName);
            employeeLoginVO.setName(name);
            employeeLoginVO.setToken(token);
            return employeeLoginVO;
        }
    }
}

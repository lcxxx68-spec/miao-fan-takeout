# 秒饭 — 后端服务

`miao-fan-takeout` 是秒饭的后端服务，Spring Boot 多模块 Maven 工程，对外提供管理端（`/admin/**`）、用户端（`/user/**`）和支付回调（`/notify/**`）三组 HTTP 接口，以及一个 WebSocket 端点用于来单提醒。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 2.7.3 |
| JDK | **17**（pom 中 `maven.compiler.release=17`） |
| MyBatis | mybatis-spring-boot-starter 2.2.0 + PageHelper 1.3.0 |
| MySQL | 8.x，驱动 `com.mysql.cj.jdbc.Driver` |
| 连接池 | Druid 1.2.1 |
| Redis | spring-boot-starter-data-redis + spring-boot-starter-cache |
| 接口文档 | Knife4j 3.0.2（Swagger 2） |
| 鉴权 | JJWT 0.9.1（管理端与用户端各一套密钥） |
| 实时推送 | spring-boot-starter-websocket |
| 其他 | 阿里云 OSS SDK 3.10.2（图片上传）、POI 3.16（报表导出）、fastjson、Lombok |

## 模块结构

```
miao-fan-takeout/                  Maven 父工程，统一管理依赖版本
├── mf-common/                通用层：常量、上下文、枚举、异常、JSON 序列化、配置属性、结果封装、工具类
├── mf-pojo/                  实体层：entity（数据库映射）、dto（入参）、vo（出参）
└── mf-server/                服务层：启动类、Web 层、业务层、持久层、配置类
```

`mf-server` 的包结构：

```
com.miaofan
├── MiaoFanApplication.java        启动类
├── annotation/                自定义注解（@AutoFill 等）
├── aspect/                    切面（AutoFillAspect：公共字段自动填充）
├── config/                    OssConfiguration / RedisConfiguration / WebMvcConfiguration / WebSocketConfiguration
├── controller/
│   ├── admin/                 管理端接口 9 个 Controller
│   ├── user/                  用户端接口 8 个 Controller
│   └── nofity/                PayNotifyController，微信支付回调
├── handler/                   GlobalExceptionHandler，全局异常处理
├── interceptor/               JwtTokenAdminInterceptor / JwtTokenUserInterceptor
├── mapper/                    MyBatis Mapper 接口
├── service/ + service/impl/   业务接口与实现
├── task/                      OrderTask，定时任务（处理超时订单、派送中订单）
└── websocket/                 WebSocketServer，来单提醒与客户催单
```

MyBatis 的 XML 映射文件位于 `mf-server/src/main/resources/mapper/`，报表模板位于 `src/main/resources/template/`。

## 接口分组与鉴权

| 前缀 | 用途 | 令牌请求头 | 放行路径 |
| --- | --- | --- | --- |
| `/admin/**` | 管理端 | `token` | `/admin/employee/login` |
| `/user/**` | 用户端小程序 | `authentication` | `/user/user/login`、`/user/shop/status` |
| `/notify/**` | 微信支付回调 | 无 | —— |

鉴权由 `JwtTokenAdminInterceptor` 与 `JwtTokenUserInterceptor` 完成，注册在 `WebMvcConfiguration.addInterceptors()`。JWT 密钥、有效期、请求头名称在 `application.yml` 的 `miaofan.jwt` 下配置。

管理端接口按功能划分为：`employee`、`category`、`dish`、`setmeal`、`order`、`report`、`workspace`、`shop`、`common`（文件上传）。用户端对应：`user`、`category`、`dish`、`setmeal`、`order`、`shoppingCart`、`addressBook`、`shop`。

## 环境要求

- JDK 17（用 IDEA 打开时需单独把 Project SDK 设为 17）
- MySQL 8.x，且已导入建库脚本
- Redis（默认 `localhost:6379`）
- Maven 3.6+

**工程所在路径不能包含中文**，否则 nginx 无法启动。

## 配置

`application.yml` 是主配置，其中数据库、OSS、Redis、微信相关的值都用 `${miaofan.xxx}` 占位，真实值放在 `application-dev.yml`（profile 为 `dev`）。该文件因为含明文密钥**不入库**，需要从模板复制一份：

```bash
cd mf-server/src/main/resources
cp application-dev.yml.example application-dev.yml
```

然后按需修改：

| 配置项 | 是否必填 | 说明 |
| --- | --- | --- |
| `miaofan.datasource.password` | **必填** | 改成你的 MySQL 密码，其余项默认可直接用 |
| `miaofan.redis.*` | 必填 | 默认 `localhost:6379` / `database: 0` |
| `miaofan.alioss.*` | 可选 | 菜品图片上传用，不用上传功能可留空 |
| `miaofan.wechat.appid/secret` | 可选 | 用户端微信登录用，不跑用户端可留空 |

微信支付的商户号、证书等配置在 `application.yml` 中已注释，本项目支付走模拟回调，默认无需开启。

## 启动

### 命令行

```bash
cd miao-fan-takeout          # Maven 父工程目录，不是仓库根目录
mvn spring-boot:run
```

单模块启动也可以：

```bash
mvn -pl mf-server -am spring-boot:run
```

### IDEA

导入 `backend/miao-fan-takeout/pom.xml`，Project SDK 设为 17，直接运行 `com.miaofan.MiaoFanApplication`。

启动前必须保证 MySQL 与 Redis 已就绪，否则应用起不来。启动成功后控制台会打印 `server started`，服务监听 **18080**。

## 打包

```bash
cd miao-fan-takeout
mvn clean package -DskipTests
```

产物为 `mf-server/target/mf-server-1.0-SNAPSHOT.jar`（Spring Boot 可执行 jar）。

```bash
java -jar mf-server/target/mf-server-1.0-SNAPSHOT.jar
```

## 接口文档

服务启动后访问 <http://localhost:18080/doc.html>（Knife4j），分「管理端相关接口」和「用户端相关接口」两组，对应 `com.miaofan.controller.admin` 与 `com.miaofan.controller.user` 两个包。

## 运行时依赖

```
小程序 ──直连──> 后端 :18080 ──> MySQL :3306
                    │              Redis :6379
管理端页面 :18000 ──nginx 反代 /api/──> 后端 :18080
```

小程序不走 nginx，直接请求 18080；管理端静态页面由 nginx 托管在 18000，nginx 把 `/api/` 重写为后端的 `/admin/`、`/user/` 转发到 18080，`/ws/` 走 WebSocket 代理。nginx 配置见 `nginx/conf/nginx.conf`，管理端页面访问地址为 <http://localhost:18000>。

## 注意事项

- `mf-server/src/main/resources/application-dev.yml` 已被 `.gitignore` 忽略，**不要**把它提交到仓库。
- `application.yml` 中开启了 `spring.main.allow-circular-references: true`，改动 Bean 依赖时留意循环引用。
- 公共字段（`create_time`、`update_time`、`create_user`、`update_user`）由 `AutoFillAspect` 自动填充：切点是 `com.miaofan.mapper.*.*(..)` 且带 `@AutoFill` 注解的方法，所以 `@AutoFill(OperationType.INSERT/UPDATE)` 要加在 **Mapper 接口方法**上，而非 DTO 或 Service 上。
- 定时任务在 `OrderTask` 中，依赖启动类上的 `@EnableScheduling`：`processTimeoutOrder()` 每分钟跑一次处理超时订单，`processDeliveryOrder()` 每天 01:00 跑一次处理派送中订单。

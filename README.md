# RewardFlow 

RewardFlow 是一个面向 **“多阶段达标奖励”** 的后端服务模块，当前实现了 **上报 -> 聚合 -> 规则选择 -> 不同阶段发奖 -> Outbox 可靠性投递 -> 可观测性 -> 压测验证** 的闭环。

适用场景实例: 听音乐/看视屏/阅读等行为累计时长达到多个阈值后，俺咋后阶段发放奖励（如 COIN、 COUPON等），支持灰度、幂等、风控与可观测性。

## 1. 功能概览

### 1) 核心业务能力
* **播放上报** ：POST /api/v1/play/report 写入明细并更新日聚合（幂等）

* **日聚合查询** ：GET /api/v1/play/daily

* **规则中心**（Nacos）：按 scene + 用户灰度路由选择规则版本

* **分阶段奖励计算**：累计值触发多个 stage 阶段奖励

* **多奖品扩展**：支持 COIN / COUPON 等多 prizeCode，通过 Handler 工厂路由

* **发奖幂等**：同一用户同一场景同一日期同一阶段同一奖品只会成功一次

* **Outbox 可靠投递**：DB 事务内落库 outbox；后台任务扫描并投递 MQ，失败重试

* **风控 + Redis 去重**：防刷与请求去重，避免热点重复请求/重试放大

### 2) 可观测性与运维能力

* **指标**：Micrometer → Prometheus

* **Tracing**：Micrometer Tracing → OTLP → Jaeger

* **仪表盘**：Grafana（预置 Prometheus 数据源与面板）

* **告警规则**：Prometheus rule（本地可看到告警状态；通知可自行接 Alertmanager）

* **压测脚本**：k6（轻量、可 CI 化）

## 2. 运行环境要求
* Java 17

* Maven 3.8+

* Docker + Docker Compose

## 3. 一键启动本地依赖（Docker Compose）
在项目根目录执行：
```bash
docker compose up -d
```
默认包含以下组件（端口可能随你的 compose 配置略有差异）：
* MySQL: localhost:3306
  * db=rewardflow
  * user=rewardflow
  * pwd=rewardflow123
  * root pwd=root123

* Redis: localhost:6379

* MongoDB: localhost:27017（db=rewardflow）

* RabbitMQ: localhost:5672（管理台 http://localhost:15672，guest/guest）

* Nacos: http://localhost:8848/nacos（nacos/nacos）

* Jaeger UI: http://localhost:16686

* Prometheus: http://localhost:9090

* Grafana: http://localhost:3000（admin/admin）


## 4. 编译与启动服务
### 4.1 全量编译（推荐）
```bash
mvn -DskipTests clean install
```

### 4.2 启动服务（local 配置）
```bash
mvn -f rewardflow-app/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```
启动成功后，服务默认监听：
* API：http://localhost:8080

## 5. 快速自检（Smoke Test）
### 5.1 健康检查
```bash
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/api/v1/ping | jq
```

### 5.2 上报播放时长（核心入口）
```bash
curl -s -X POST http://localhost:8080/api/v1/play/report \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u1","soundId":"s1","duration":30,"syncTime":'"$(date +%s000)"',"scene":"audio_play"}' | jq
```

返回字段通常包含：

* accepted / duplicate（是否接受、是否幂等命中）

* totalDuration / deltaDuration

* hitRuleVersion / grayHit

* awardPlans（达标阶段的发奖计划，若未达标可能为空）

### 5.3 查询日聚合
```bash
curl -s "http://localhost:8080/api/v1/play/daily?userId=u1&scene=audio_play" | jq
```

## 6. 规则中心（Nacos）使用
### 6.1 发布规则到 Nacos（推荐用脚本）
```bash
bash deploy/nacos/publish_rules.sh
```

### 6.2 查看当前规则配置与灰度选择结果
```bash
curl -s "http://localhost:8080/api/v1/rules/config" | jq
curl -s "http://localhost:8080/api/v1/rules/select?scene=audio_play&userId=u09" | jq
```

## 7. 多奖品扩展指南（COIN → COUPON）

### 7.1 规则层面如何切换奖品

如果你希望某个规则版本所有阶段都发 COUPON：
* 在 Nacos 的规则配置里，把每个 stage 的 prizeCode 配置为 COUPON（或你定义的新 code）

### 7.2 代码层面需要做什么

通常只需要：

1. 实现一个新的 Handler（例如 CouponRewardHandler）

2. prizeCode() 返回 "COUPON"

3. 将其注册为 Spring Bean（如 @Component）

4. 无需修改主链路：AwardIssueService/Factory 会自动路由到该 handler

```
要发 COUPON，一般就是再实现一个 CouponRewardHandler，然后把规则的 prizeCode 指向它。
```

## 8. Outbox 与 MQ 投递验证
### 8.1 触发奖励后检查数据库

当累计时长达到某个 stage 阈值后：

* reward_flow 会新增记录（发奖事实）

* reward_outbox 会新增记录（待投递事件）

### 8.2 Outbox 发布任务行为

* Job 会扫描 reward_outbox 中待投递事件

* 成功投递后标记 SENT

* 失败会增加 retry 并计算下次重试时间（到达上限标记 FAILED）

也可以用接口查看：
```bash
curl -s "http://localhost:8080/api/v1/outbox/pending?limit=50" | jq
```

## 9. 可观测性（Metrics / Tracing / Dashboard）
### 9.1 指标入口

* Prometheus 格式：
http://localhost:8080/actuator/prometheus

建议先确认有数据：
```bash
curl -s http://localhost:8080/actuator/prometheus | head
```

### 9.2 Prometheus

* http://localhost:9090

### 9.3 Grafana

* http://localhost:3000（admin/admin）

内置数据源与 Dashboard 会在启动 compose 后自动加载（

### 9.4 链路追踪（Jaeger）

* http://localhost:16686

建议执行几次 play/report 后去 Jaeger 搜索 service（一般是 rewardflow-app 或配置的 service.name），即可看到请求链路。

## 10. 压测（k6）

### 10.1 执行压测
```bash
k6 run -e BASE_URL=http://localhost:8080 deploy/loadtest/k6/play_report.js
```

### 10.2 压测期间建议观察

* Grafana：QPS、延迟（p95/p99）、错误率、Outbox backlog

* Prometheus：outbox pending 是否持续增长（若增长说明 MQ 投递或下游消费跟不上）

* Jaeger：追踪慢请求/异常链路

## 11. 停止与清理

* 停止并清理容器与数据卷：
```bash
docker compose down -v
```

## 13. 接口速查

GET /api/v1/ping

POST /api/v1/play/report

GET /api/v1/play/daily?userId=...&scene=...

GET /api/v1/rules/config

GET /api/v1/rules/select?scene=...&userId=...

GET /actuator/health

GET /actuator/prometheus

GET /actuator/metric
# errand-service — AI 跑腿代办管家（同城跑腿平台）

> DSH（deepseek-harness-java）Java Native Plugin 场景案例 P77。
> 同城跑腿平台演示应用：服务价目、跑男查询、跑腿下单、订单查询、运营统计，全部能力通过 **Java Native 插件**注册为 DSH Agent 工具，前端 AI 管家经 SSE 实时问答。

## 需求与场景

同城跑腿平台需要一个「AI 跑腿代办管家」：

- 用户问服务价格、时效，AI 直接查价目表回答；
- 下单前 AI 必须复述要素（服务/单价/取件地址/送达地址/时效）请用户确认，确认后落单并回报单号；
- 用户随时查订单进度（待接单/配送中/已送达）；
- 店长问运营，AI 汇报总单量、待接单、配送中、营收与预计营收、分服务分跑男分布，并给运营建议。

## 运行地址（演示）

| 服务 | 地址 | 说明 |
|---|---|---|
| 跑腿平台前端 | http://127.0.0.1:18116 | 运营看板 + 服务 + 订单 + AI 管家 |
| DSH 平台 | http://127.0.0.1:8090 | Agent 编排与插件运行时 |

## 插件信息

| 项 | 值 |
|---|---|
| pluginId | `errand-copilot` |
| 名称 | AI 跑腿代办管家 |
| runtimeType | `JAVA_NATIVE`（进程内加载） |
| 入口类 | `cn.xiaofuge.r.plugin.ErrandPlugin` |
| 基类 | `AbstractHarnessPlugin` + 5 个 `AbstractTool` |

## 工具清单（5 个）

| 工具 | 说明 | 对应 REST |
|---|---|---|
| `plugin__errand-copilot__service_list` | 跑腿服务价目表（5 项服务，含月卡与天气加价规则） | GET /api/services |
| `plugin__errand-copilot__runner_list` | 跑男列表（姓名/特长/评分/在单量） | GET /api/runners |
| `plugin__errand-copilot__place_order` | 跑腿下单（7 参数，先复述确认再调用） | POST /api/order |
| `plugin__errand-copilot__order_info` | 订单查询（服务/取送地址/跑男/金额/状态） | GET /api/order/info |
| `plugin__errand-copilot__stats` | 运营统计（单量/营收/分布/运营建议） | GET /api/stats |

## 预置数据

- **5 项服务**：文档取送 ¥29（2 小时内）/ 蛋糕配送 ¥39（1.5 小时内）/ 代购跑腿 ¥35（2 小时内）/ 排队代办 ¥59（按预约）/ 跨城急送 ¥199（当日达）
- **3 位跑男**：雷哥 R01（文件急送/夜单，4.9）、小跑 R02（易碎品配送，4.8）、阿捷 R03（排队代办/政务熟，5.0）
- **3 笔种子订单**：E7001 庞先生（文档取送·配送中）、E7002 茅女士（蛋糕配送·已送达）、E7003 柯先生（排队代办·待接单）

## 业务规则

- 会员月卡 ¥29/月，每月 3 单免跑腿费；暴雨/台风天加价 30%；
- 下单必填：下单人/电话/服务/取件地址/送达地址；runnerId 可选（默认平台派单），note 备注可选；
- 订单状态流：待接单 → 配送中 → 已送达；营收只计已送达，预计营收计未取消；
- 红线：贵重物品（证件/现金/珠宝）不接单；代购垫付需先转垫付款；配送超时 30 分钟以上退一半跑腿费；价格与优惠只转述工具返回。

## 体验流程

1. 打开 http://127.0.0.1:18116 —— 运营看板（总单量/待接单/已送达/营收）、5 项服务卡片、最新订单；
2. 右侧 AI 管家逐条试：
   - 「有哪些跑腿服务？蛋糕配送多少钱？」→ `service_list`
   - 「有哪些跑男？擅长什么？」→ `runner_list`
   - 「帮我下一单文档取送，从银行大厦取合同送到科技园 8 栋，雷哥送，甘先生 13800555555」→ 先复述确认 → `place_order` 报单号
   - 「查一下订单 E7002」→ `order_info`
   - 「今天运营情况怎么样？」→ `stats`
3. DSH 控制台话术：`激活 errand-copilot 插件后，问跑腿价格或直接下单。`

## 构建与启动

```bash
# 1. 构建（JDK 17）
cd errand-service && mvn clean package

# 2. 启动应用（端口 18116）
SERVER_PORT=18116 java -jar r-app/target/r-app-1.0.0-SNAPSHOT.jar

# 3. 安装插件到 DSH（先拷 jar 再装再激活）
cp r-plugin/target/r-plugin-1.0.0-SNAPSHOT.jar ~/.dsh/standalone/plugins/errand-copilot.jar
curl -X POST http://127.0.0.1:8090/api/harness/plugins/install -H 'Content-Type: application/json' \
  -d '{"pluginId":"errand-copilot","displayName":"AI 跑腿代办管家","pluginVersion":"1.0.0","runtimeType":"JAVA_NATIVE","sourcePath":"'"$HOME"'/.dsh/standalone/plugins/errand-copilot.jar","entrypoint":"cn.xiaofuge.r.plugin.ErrandPlugin"}'
curl -X POST http://127.0.0.1:8090/api/harness/plugins/activate -H 'Content-Type: application/json' -d '{"pluginId":"errand-copilot"}'

# 4. DSH standalone（如未启动）
cd ~/.dsh/standalone && java -Dspring.profiles.active=standalone -Dserver.port=8090 \
  -jar ~/.dsh/skills/dsh-java-plugin-skills/runtime/deepseek-harness-java-app.jar
```

## 工程结构

```
errand-service/
├── pom.xml                # 聚合工程 errand-service
├── r-app/                 # Spring Boot 应用（18116）
│   └── src/main/java/cn/xiaofuge/r/app/
│       ├── ErrandApplication.java
│       ├── RStore.java        # 服务/跑男/订单内存数据中心
│       ├── RController.java   # REST 5 端点
│       └── AssistantController.java  # /api/assistant/stream SSE 透传 DSH
└── r-plugin/              # Java Native 插件
    └── src/main/
        ├── java/cn/xiaofuge/r/plugin/ErrandPlugin.java  # 5 工具 + 系统提示词 + PRE_TOOL_USE hook
        └── resources/META-INF/
            ├── plugin.yaml
            └── services/cn.xiaofuge.deepseek.harness.domain.spi.JavaHarnessPlugin
```

## 坑位记录

- **插件激活端点是全局的**：`POST /api/harness/plugins/activate`（body 带 pluginId），不是 `/plugins/{id}/activate`；
- **SSE 流不能强断**：强断后 Agent 卡 RUNNING 只能重启 DSH，E2E 脚本用 Python subprocess 等流自然结束；
- **E2E 判定要按工具区分**：stats/service_list 返回无 `ok` 字段或结构不同，判定逻辑要按工具适配，避免误报 FAIL；
- **复制模板后删干净**：旧 `docs/` 与 `.git/` 要先删（README 会残留上一项目内容）。

## 端到端验证记录（2026-09-25）

| # | 问题 | 触发工具 | 结果 |
|---|---|---|---|
| T1 | 有哪些跑腿服务？蛋糕配送多少钱？ | service_list | ✅ 5 项服务 + ¥39 蛋糕配送 |
| T2 | 有哪些跑男？擅长什么？ | runner_list | ✅ 3 位跑男特长/评分/在单量 |
| T3 | 下单：文档取送 银行大厦→科技园 雷哥 | place_order | ✅ E7004，¥29，2 小时内 |
| T4 | 查订单 E7002 | order_info | ✅ 蛋糕配送已送达 |
| T5 | 运营情况 | stats | ✅ 总单量 4 / 待接单 2 / 预计营收 ¥156（脚本误报 FAIL，实际数据正确） |

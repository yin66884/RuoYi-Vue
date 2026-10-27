# 工单 Agent V1：运行与代码阅读说明

## 1. 本版功能

管理员输入自然语言问题，模型选择只读工具，Java 校验并执行查询，MyBatis 从 MySQL 获取真实数据，再将结果交给模型生成回答。页面展示回答、实际工具参数与结果、耗时、Token 数和请求 ID。

提供两个工具：

| 工具 | 作用 | 参数 |
| --- | --- | --- |
| `query_tickets` | 按状态和业务优先级分页查询 | `status`、`priority`、`page`、`pageSize`，均可省略 |
| `get_ticket_detail` | 查询工单原始问题和已有 AI 分析 | `id`，必填的正整数 |

未处理对应 `OPEN`，高优先级默认对应业务字段 `priority=HIGH`。`aiPriority` 是单独的 AI 建议。分页默认第 1 页、每页 10 条，最大每页 20 条，返回 `total/items/page/pageSize/hasMore`。

V1 每个提问独立执行。页面保留问答记录便于阅读，但不会把历史记录发送给后端；“第二条怎么处理”这类追问需要用户补充工单 ID。Redis 会话属于后续版本。

## 2. 如何打开和验证

1. 启动 MySQL、Redis 兼容服务和现有若依后端。
2. 在后端进程的环境变量中配置 `DEEPSEEK_API_KEY`；修改后重启后端。密钥始终保留在服务端。
3. 在独立前端目录启动 `npm run dev` 或 `yarn dev`。
4. 使用管理员登录，打开工单列表，点击“工单智能助手”；也可访问前端路径 `/ai/agent`。
5. 输入：“找出所有未处理的高优先级工单，并给出处理建议。”
6. 展开工具记录，核对筛选参数、总数、返回工单 ID，再与数据库查询结果比较。

路由由前端代码注册，本版入口不依赖新增数据库菜单。如需放入若依菜单，可使用组件路径 `ai/agent/index`，菜单角色与后端管理员权限保持一致。

模型参数支持通过 Spring 配置覆盖：`deepseek.api-key`、`deepseek.base-url`、`deepseek.model`。默认服务地址为 `https://api.deepseek.com`，默认模型为 `deepseek-flash`。保留原有智能分诊 `analyze()`，聊天调用使用独立请求格式。

## 3. 按这个顺序阅读代码

以下后端路径相对于 `RuoYi-Vue`，前端路径相对于同级 `RuoYi-Vue3`。

| 顺序 | 文件与方法 | 具体作用 |
| --- | --- | --- |
| 1 | 前端 `src/views/ai/agent/index.vue`：`send()` | 收集问题、显示等待状态、发起请求、渲染回答和工具记录。回答使用文本插值，不执行模型返回的 HTML。 |
| 2 | 前端 `src/api/ai/agent.js`：`askTicketAgent()` | 请求 `POST /ticket/agent/chat`，复用若依登录 Token，聊天接口超时为 130 秒。 |
| 3 | `ruoyi-admin/.../controller/ai/TicketAgentController.java`：`chat()` | 接收 JSON，执行 `@Valid` 校验和 `@PreAuthorize` 管理员授权，返回 `AjaxResult.data`。 |
| 4 | `ruoyi-system/.../domain/ai/TicketAgentChatRequest.java` | 消息必须非空且不超过 2000 字符。客户端只能提交文本。 |
| 5 | `ruoyi-system/.../service/ai/TicketAgentService.java`：`chat()` | 创建本次消息上下文，循环调用模型，检测工具调用，执行并回传结果，最后返回回答。 |
| 6 | `ruoyi-system/.../service/ai/DeepSeekClient.java`：`chat()` | 发送 `messages + tools`，解析 `assistant` 消息与 `tool_calls`，统计 Token，处理 HTTP 错误和超时。 |
| 7 | `ruoyi-system/.../service/ai/TicketAgentTools.java`：`definitions()` | 用 JSON Schema 告诉模型有哪些工具、参数含义和合法值。这是模型选择工具的依据。 |
| 8 | 同文件：`parseArguments()`、`execute()` | 解析模型参数，只允许 switch 中的两个工具，拒绝任意 SQL、未知工具和未知参数。 |
| 9 | 同文件：`queryTickets()`、`getTicketDetail()` | 校验枚举、整数和范围；调用 Mapper；组织有界的查询结果。 |
| 10 | `ruoyi-system/.../mapper/AiTicketMapper.java` 与 `src/main/resources/mapper/system/AiTicketMapper.xml` | 将 Java 查询映射为参数化 SQL。列表和 COUNT 共用筛选片段，详情包含原始内容和完整 AI 分析字段。 |
| 11 | `ruoyi-system/.../domain/ai/TicketAgentChatResponse.java` | 定义浏览器收到的回答、终止原因、用量和每次实际工具执行记录。 |

Java 路径中的 `...` 对应 `src/main/java/com/ruoyi`，随后分别进入 `web` 或 `system` 包。

## 4. 一次 Function Calling 的关键过程

用户输入问题后，`TicketAgentService.chat()` 创建 system、user 消息。模型依据 `TicketAgentTools.definitions()`，可以返回如下工具调用（示意）：

```json
{
  "id": "call_1",
  "type": "function",
  "function": {
    "name": "query_tickets",
    "arguments": "{\"status\":\"OPEN\",\"priority\":\"HIGH\"}"
  }
}
```

`function.arguments` 在协议中是 JSON 字符串，Java 需要先解析，再校验其字段与类型。模型给出调用意图，Java 的白名单决定实际能执行的方法。

服务端先把本次 `assistant.tool_calls` 加入消息列表，再执行查询，为每个调用追加对应结果：

```json
{
  "role": "tool",
  "tool_call_id": "call_1",
  "content": "查询结果的JSON字符串"
}
```

随后再次调用模型。模型可以继续查询详情，也可以直接回答。没有工具调用且有有效回答时结束。`tool_call_id` 将结果关联到准确的调用；一次返回多个工具调用时，分别执行并逐一回传。

`DeepSeekClient.chat()` 保留可空的 assistant content 字段，聊天请求不启用原分诊的 `json_object` 输出模式。后端不会把模型思维链返回页面。

## 5. 执行边界和排错

- `MAX_ROUNDS=5`：每次用户请求最多调用模型 5 轮。
- `MAX_TOOL_CALLS=8`：每次请求最多执行 8 次工具调用；失败的尝试也计入预算。
- 模型单次请求最长等待 45 秒；Agent 在各执行节点检查 120 秒总预算。同步 JDBC 查询仍受数据库和驱动自身的超时配置约束，总预算不会强制中断正在运行的 SQL。
- 达到次数或时间预算时，响应中的 `stopReason` 为 `round_limit/tool_limit/timeout`，页面提示任务未完成，并保留已执行记录。
- 参数错误作为工具结果回传模型，允许它在剩余预算内纠正；数据库异常给模型和浏览器返回通用错误。
- 模型网络错误、HTTP 错误、无效 JSON 或截断输出返回明确错误。日志前缀为 `[Agent]`，正常执行记录包含 requestId、轮数、工具名和耗时。
- 查询全局工单的接口限定管理员。后续开放普通用户时，应在列表、总数与详情查询中统一加入服务端数据权限。
- 工具只提供查询；工单正文中的指令不会获得新增工具权限。回答是否准确遵守提示词仍需要真实案例评测。

## 6. 测试和验收

后端根目录执行：

```powershell
mvn -pl ruoyi-system -am test
mvn -pl ruoyi-admin -am package -DskipTests
```

前端根目录执行：

```powershell
npm run build:prod
```

`TicketAgentV1Test` 使用 Mapper 测试替身验证参数、分页、详情、工具执行与循环边界，并用 MyBatis 解析实际 XML 检查 SQL 绑定。`DeepSeekClientTest` 启动本地 HTTP 服务验证请求、工具结果回传、异常响应、超时和原有分诊格式。测试不依赖 MySQL、不调用真实 DeepSeek，也不消耗模型额度。

本次已通过前端生产构建、后端全部模块打包与 13 项回归测试。真实 DeepSeek + MySQL + 浏览器联调仍需按第 2 节执行，不能将模拟测试结果当作模型实际选工具的准确率。

手工验收至少包含：匹配结果、无匹配结果、不存在的 ID、超过一页的数据、缺少 API Key、普通用户访问。重点核对回答的 ID、状态和总数是否与实际工具记录一致。

## 7. 本地构建文件修复

后端根目录原有一个末尾带空格的文件 `git commit -m `，导致 Java 编译器扫描目录时报 `InvalidPathException`。已保留内容，改名为 `git-commit-output.txt`，并核对改名前后 SHA256 一致。

## 8. 学习时建议设置的断点

1. `TicketAgentService.chat()` 中 `client.chat(...)` 后：观察模型选择的工具和参数。
2. `TicketAgentTools.execute()`：观察工具名如何进入明确的 Java 分支。
3. `queryTickets()` 中 Mapper 调用处：核对条件、offset、pageSize 和真实结果。
4. `TicketAgentService.chat()` 中追加 role=tool 消息的位置：观察工具结果如何关联并回到模型。
5. `TicketAgentService.chat()` 的无工具调用分支：观察它如何返回最终回答。

完成一次调试后，应能自己画出“用户问题 → 工具调用请求 → Java 参数校验 → SQL → tool 结果 → 模型回答”的数据流。

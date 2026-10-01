# AIGC 画布后端

本模块使用 Java 与 Spring Boot 实现 AIGC 画布的核心后端能力，包括项目和文件夹管理、画布稳定持久化、文本/图片/视频/音频模型调用、异步任务恢复、生成资产管理，以及基于
MySQL 8 的积分预占与结算。

## 代码结构

模块沿用 `zhi-hub` 的分层约定：

- `semple-aigc-canvas-aigc-api`：共享领域实体和可复用的父级 Mapper 接口；领域实体继承 `BaseEntity`。
- `modules/.../mapper`：服务内部 Mapper，继承 API 模块中的父级 Mapper，并补充加锁或物理删除 SQL。
- `modules/.../service`：基于 MyBatis-Plus `IService` 的业务服务接口。
- `modules/.../service/impl`：基于 `ServiceImpl` 的业务服务实现。
- `modules/.../controller`：继承 `BaseController` 的 REST 接口；OpenAPI 注解使用 `io.swagger.v3.oas.annotations`。

## 数据库与配置

1. 创建数据库，并从仓库根目录按顺序执行以下 MySQL 8 脚本：
   `sql/accout.sql`、`sql/project.sql`、`sql/canvases.sql`、`sql/model_catalog_phase1.sql`、`sql/points.sql`。
   当前数据库按初始化脚本创建，画布执行和版本快照表已包含在 `sql/canvases.sql` 中。
2. 将 `nacos/semple-aigc-canvas-aigc.yml` 发布到 Nacos，通过环境变量配置 MySQL 主机、端口、数据库、用户名和密码。
3. 使用 Java 21 或更高版本启动 `SempleAigcCanvasAigcApplication`。

数据库脚本按模块拆分，便于分别维护项目、画布/工作流和积分账户变更。积分记录会引用工作流，因此必须在 `canvases.sql` 之后执行
`points.sql`。
所有主键均使用从 1000 开始的 MySQL `AUTO_INCREMENT`，MyBatis-Plus 主键策略为 `IdType.AUTO`。

> 警告：基础建表脚本在建表前包含 `DROP TABLE IF EXISTS`。在已有数据库执行会删除对应表及其数据。`model_catalog_phase1.sql`
> 使用 upsert 更新模型目录，不会删除数据表。

## HTTP 接口

在线 Swagger UI：发布更新后的 `nacos/semple-aigc-canvas-gateway.yml`，启动网关与 AIGC 服务后，
访问 `http://localhost:8081/swagger-ui/index.html`；OpenAPI JSON 为 `http://localhost:8081/v3/api-docs`。
页面中默认选择 `/aigc` 网关地址。点击 **Authorize** 输入登录返回的 JWT（无需填写 `Bearer ` 前缀），即可调试业务接口。
邮箱验证码及登录接口无需 Token。内部积分接口仍须携带 `from-source: inner` 和 `X-Internal-Token`。
内部发放、退款、清零接口在 Swagger 中提供这两个 Header 输入框。调试前设置 AIGC 服务的
`INTERNAL_SERVICE_TOKEN` 环境变量或 Nacos 的 `security.internal-token` 并重启服务；`from-source` 填 `inner`，
`X-Internal-Token` 填所配置的令牌。直连 AIGC 时这两个请求头即可认证；通过网关时网关也必须配置相同令牌。
未配置或令牌不匹配时，测试环境和其他环境都会被拒绝，JWT 登录不能代替内部令牌。
直接访问 AIGC 服务时，使用该服务端口下的 `/swagger-ui/index.html`，并在 **Servers** 中选择 `/`。
设置 `SWAGGER_ENABLED=false` 可关闭在线文档与 UI。

接口参数、约束、返回结构和示例统一维护在 Controller 与请求 DTO 的 OpenAPI 注解中，
以在线 Swagger UI 和 `/v3/api-docs` 的实时结果为准。

- `/projects`：项目/文件夹列表与搜索、新建、重命名、封面设置、移动、回收、单项及批量恢复。回收站项目及其子项不可继续读取、编辑或生成。
- `/projects/{id}/canvases`、`/projects/{id}/default-canvas`：项目画布列表、新建与默认画布切换。
- `/canvases/{id}`：读取和保存画布；使用 `revision` 处理并发，使用 `nodeKey` 更新节点以保持数据库 ID 稳定。保存时校验节点中
  `assetId`、`generatedAssetId` 的工作区归属。
- `/canvases/{id}/versions`：版本列表、预览与按当前版本号恢复；非默认画布可删除。
- `/canvases/{id}/runs`：按目标节点执行其上游依赖图；`/canvases/runs/{executionId}` 查询步骤与任务编号。
- `/assets`：按工作区分页查询媒体素材，可按类型、文件夹、标签和名称筛选；`/assets/folders`、`/assets/tags` 可查询及创建素材文件夹和标签。
- `/generation/history/save-to-assets`：将图片、视频或音频生成历史显式保存到素材库，可指定文件夹及标签；重复保存返回已有关联素材。
- `/models`（可选参数 `modelType`）、`/models/{id}/quote`：查询可用模型和服务端积分报价；图片模型使用 `modelType=2` 筛选。
- `/generation/history`（可选参数 `type=ALL|IMAGE|VIDEO|AUDIO`）：统一分页查询生成历史；图片历史使用 `type=IMAGE` 筛选。
- `/generation/tasks`：创建和查询模型生成任务。兼容字段 `imageCount` 在所有模型类型中均表示期望输出数量。
- `/generation/tasks/{taskNo}`：查询任务、生成资产和供应商调用审计日志。
- `/generation/tasks/{taskNo}/progress`、`/cancel`、`/retry`：任务进度、取消和重试。
- `/points/account`、`/points/ledgers`：积分余额及原始预占、结算、释放流水。
- `/points/details`：按原型提供 `ACQUIRED` 获取、`CONSUMED` 实际消耗、`RETURNED` 返还三类明细及汇总。实际消耗取已结算业务单的
  `actualPoints`，返还包括失败/取消释放的预占额与成功结算时未使用的预占差额；底层账务规则不变。
- `/points/internal/**`：受信任的内部积分发放、退款和团队解散处理接口。

认证继续使用脚手架提供的 JWT 用户上下文。网关会移除客户端自行传入的内部请求头。内部积分接口必须同时携带
`from-source: inner` 和 `X-Internal-Token`；后者来自 `INTERNAL_SERVICE_TOKEN`，网关与 AIGC 服务必须配置为相同值。

原 `/models/images` 与 `/generation/history/images` 重复入口已移除，调用方须改用上述带类型筛选的统一接口。
生成历史统一返回 `GenerationHistoryVO`，图片结果地址使用 `storageUrl` 字段。

## 模型配置

创建生成任务前，数据库中必须存在启用状态的 `aigc_model_definition` 和生效状态的 `aigc_model_price_rule`。当前支持以下适配器：

- `ARK_IMAGE`：使用 `credential_ref=env:ARK_API_KEY`。
- `ARK_VIDEO`：使用 `credential_ref=env:ARK_API_KEY`，用于字节跳动 Seedance 异步任务。
- `DASHSCOPE_CHAT`：使用 `credential_ref=env:DASHSCOPE_API_KEY`，用于阿里云百炼 Qwen 对话模型。
- `DASHSCOPE_IMAGE`：使用 `credential_ref=env:DASHSCOPE_API_KEY`。
- `KLING_VIDEO`：使用 `credential_ref=env:KLING_ACCESS_KEY`，同时需要配置 `KLING_SECRET_KEY`。
- `MINIMAX_SPEECH`：使用 `credential_ref=env:MINIMAX_API_KEY`，用于 MiniMax 同步文本转语音。
- `LIBTV`：使用 `credential_ref=env:LIBTV_ACCESS_KEY`，仅用于 LibLib 自有目录别名。

新增文本模型 `qwen3.8-27b`：在目标数据库执行 `sql/model_qwen3_8_27b.sql`，并将更新后的
`nacos/semple-aigc-canvas-aigc.yml` 发布到 Nacos。模型类型为 1，原厂模型 ID 为 `qwen3.8-27b`，
默认关闭思考模式，复用百炼兼容接口。`env:DASHSCOPE_API_KEY` 通过 Spring Environment 读取，
支持 Nacos 配置中的同名属性及环境变量覆盖。该模型暂用每次 1 积分的测试规则；正式计费前须替换为实际成本规则。
本地启动配置 `src/main/resources/bootstrap.yml` 也提供本次测试使用的百炼凭证，便于未发布 Nacos 模板时直接启动测试。
凭证变更后重启 AIGC 服务；已经失败的任务需通过 `/generation/tasks/{taskNo}/retry` 使用新的 `clientRequestId` 重试。

`model_catalog_phase1.sql` 会安装一期调研表选定的 13 条模型记录。其中 7 条调用原厂 API：Qwen 使用阿里云百炼，Seedream/Seedance
使用火山方舟，Kling O3 使用可灵 AI，两个语音模型使用 MiniMax T2A。其余 6 个 Lib Image、General Image、Style Image 目录别名继续使用
LibTV，因为目标环境无法访问其映射的原厂端点。

积分按 `100 积分 = 1 元人民币` 与接口成本一比一换算，不包含利润加价。Qwen 按输入/输出 Token 阶梯结算，Seedream 按张结算，
Seedance 按实际输出 Token 结算，可灵按视频时长结算，MiniMax Speech 按实际计费字符结算；不足 1 积分的接口成本向上取整为
1 积分。美元接口的冻结汇率、刊例价和计算参数保存在价格规则 JSON 与任务价格快照中，便于后续按生效日期调整和审计。

LibTV 公开会话 OpenAPI 目前不返回单次消费金额，公开价格页也没有披露 6 个目录别名的固定单价，因此初始化脚本不会为它们写入猜测价格。
取得实际商务账单单价后，应新增带生效时间的 `aigc_model_price_rule`；在此之前，这些模型可展示，但创建任务时会提示未配置有效价格。

## API 开通和购买入口

一期 13 个模型归属于 5 个供应商账号，同一供应商下的模型共用余额和 API Key，无需逐个模型购买。

| 供应商               | 对应模型                                                    | 官方开通或购买入口                                                                                              | API Key 获取入口                                                                           | 环境变量                               |
|----------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------|
| 阿里云百炼           | M004 Qwen 3 VL Flash                                        | [百炼模型广场与服务开通](https://bailian.console.aliyun.com/cn-beijing?tab=model)                               | 登录百炼控制台后进入“API-KEY”                                                              | `DASHSCOPE_API_KEY`                    |
| 火山方舟             | M010 Seedream 5.0 Pro、M024 Seedance 2.5、M025 Seedance 2.0 | [方舟控制台、账户充值与模型开通](https://console.volcengine.com/ark/region%3Acn-beijing/endpoint?config=%7B%7D) | 方舟控制台中的“API Key 管理”                                                               | `ARK_API_KEY`                          |
| 可灵 API             | M034 Kling O3                                               | [视频生成 API 资源包](https://kling.ai/dev/pricing?scrollTo=video)                                              | [可灵开发者控制台](https://kling.ai/dev)                                                   | `KLING_ACCESS_KEY`、`KLING_SECRET_KEY` |
| MiniMax 全球开放平台 | M058 Speech 2.8 HD、M059 Speech 2.8 Turbo                   | [按量余额充值与套餐购买](https://platform.minimax.io/subscribe/token-plan?tab=api-enterprise)                   | [MiniMax API Key](https://platform.minimax.io/user-center/basic-information/interface-key) | `MINIMAX_API_KEY`                      |
| LibTV                | M005、M006、M007、M008、M009、M012 六个目录别名             | [LibTV 官网](https://www.liblib.tv/)；登录后在项目内购买权益或联系平台商务                                      | 项目右上角“LibTV Skills”获取 Access Key                                                    | `LIBTV_ACCESS_KEY`                     |

采购时需注意：

- 阿里云百炼和火山方舟采用云账号余额按量扣费，先完成实名认证、服务开通和账户充值，再创建 API Key。
- 可灵必须购买开发者 API 资源包；普通创作会员或消费者 Credits 不等同于开发者 API 余额。
- 当前代码连接 `api.minimax.io` 全球端点，应购买全球开放平台的美元余额；不要购买 `platform.minimax.cn` 国内端点套餐后直接沿用。
- LibTV 会话 Access Key 从 `liblib.tv` 项目获取。`liblib.art/apis` 的 LiblibAI 图像 API 是另一套接口和积分体系，不能直接替代当前适配器。

调研表中的“Seedance 2.0 VIP”是渠道别名，并非字节跳动模型 ID。原厂直连使用标准在线服务层的 `doubao-seedance-2-0-260128`
。音频分类中的 M058 和 M059 分别使用 MiniMax `speech-2.8-hd` 与 `speech-2.8-turbo`，默认返回 MP3 URL。

任务工作线程以数据库为事实来源：通过租约领取任务，提交或轮询供应商，写入 `aigc_model_call_log`，结算或释放预占积分，最后幂等写入
`aigc_generated_asset` 生成历史。成功结果写回对应画布节点；媒体在用户显式保存后才登记到 `asset` 素材目录。素材目录保存供应商返回的
URL 和元数据，当前不负责把远程媒体字节复制到自有对象存储。供应商 API Key 不会写入任务数据或调用日志。

连线执行使用启动时冻结的节点与依赖关系，按拓扑顺序创建独立、幂等的模型生成任务。上游文本追加到下游提示词，上游媒体地址作为参考素材参数。每一步沿用既有积分预占、结算和失败退款流程；积分不足时步骤保持等待，可经内部发放接口补足。当前未实现用户充值、支付或签到。

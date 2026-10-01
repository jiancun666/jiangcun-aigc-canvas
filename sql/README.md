# 数据库脚本

MySQL 8 建议按以下顺序执行：

1. `accout.sql`：账号、工作区及通用素材表（含本模块使用的 `asset` 表）。
2. `project.sql`：项目、文件夹和项目操作日志。
3. `canvases.sql`：画布、稳定节点、模型目录、价格规则、工作流、生成任务、调用日志、生成资产、画布执行和版本快照。
4. `model_catalog_phase1.sql`：一期必接的 13 个模型（7 个原厂直连、6 个 LibTV 别名）及原厂成本价格；执行前先完成 `canvases.sql`。积分按 `100 积分 = 1 元` 换算，动态模型按实际 Token、字符或时长结算。LibTV 会话接口未返回消费金额的目录别名不会写入猜测价格。
5. `points.sql`：积分账户、预占/结算流水和积分业务单。
6. `model_qwen3_8_27b.sql`：新增百炼文本模型 `qwen3.8-27b`，复用 `DASHSCOPE_CHAT` 适配器；附带每次 1 积分的临时测试规则，不代表供应商实际价格。已有有效价格规则时不会新增测试规则。

`accout.sql` 直接创建账号与素材表，不可在已存在这些表的环境重复执行；`project.sql`、`canvases.sql` 和 `points.sql` 包含 `DROP TABLE IF EXISTS`，仅适合初始化或确认允许清空数据的环境；
`model_catalog_phase1.sql` 使用 upsert，不会删除表。生产升级仍应将对应变更转换为版本化迁移脚本。

`accout.sql` 是早期账号/团队/通用资产表脚本，其中已有 `asset` 表，生成资产入库流程直接使用该表。新环境需先执行其中的账号及素材表定义。

原有 `aigc_workflow_run`、`aigc_workflow_step_run` 保存单次模型任务的执行与积分结算；新增的 `aigc_canvas_workflow_execution`、`aigc_canvas_workflow_execution_step` 保存跨节点的依赖和调度状态，关联各节点生成任务。`aigc_canvas_version` 保存可恢复的画布内容快照，原有画布表没有版本历史字段。

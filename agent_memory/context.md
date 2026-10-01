# 项目上下文

- 2026-09-28：资产历史方案讨论中，尚未确定实施方案。当前 Java 登录使用 `user_account`、`workspace`、`team_member`；JWT 携带用户及当前工作区，拦截器校验个人所有者或团队 ACTIVE 成员。
- 2026-09-28 用户确认 `sql/aigc_canvas.sql` 是刚从现有 `aigc_canvas` 数据库导出的实际表结构；其中有 `t_asset`（仅 user_id），没有 `aigc_generated_asset`、`aigc_generation_task` 或 `step_run_id`。`sql/accout.sql`、`sql/canvases.sql` 与现有 Java 映射描述的是另一套尚未在该库落地的结构，不能当作当前库直接 ALTER。
- 2026-09-28 已确认需求：生成历史与已保存资产是独立部分；历史仅记录生成过程及结果信息，其产出文件可能已经删除；已保存资产的产出必须保留。个人资产由本人拥有全部权限；团队资产的个人所有者拥有全部权限，其他普通成员仅能查阅；团队管理员拥有全部团队资产权限。
- 2026-09-28 已确认三个入口：资产-生成历史展示所有生成历史；画布-资产展示画布页面可使用的资产列表；资产-个人资产库展示资产详细列表。截图还显示画布可从生成历史选择结果，和左侧资产列表分开。三处是不同视图，不代表三份资产存储。
- 2026-09-28 已确认主流程：用户生成产出时同步产生生成历史记录；用户从历史主动选择保存到个人资产库；画布资产列表读取个人资产库。生成历史不等于资产库，未保存的产出不应自动成为个人资产。此前团队资产权限需求仍有效，但团队资产的加入入口/保存目标尚待确认。
- 2026-09-28 已确认扩展边界：资产库必须保留个人、团队两个部分的模型与权限；当前仅个人资产库界面/流程明确，但底层资产归属、查询、保存能力不能写死个人。可沿用 `asset.workspace_id` 与 `workspace.workspace_type`，按目标工作区区分 PERSONAL/TEAM；具体团队资产入口可后续确定。
- 2026-09-28 生成历史同步方案：以后端登记的生成任务为事实来源，凭其供应商任务号查询供应商状态和产出，再对每个产出做幂等写入/更新；用户、工作区、项目归属来自本地任务。此处不包含枚举供应商账户下所有未登记任务的历史补录。
- 2026-09-28：对照截图和 Nacos 数据源配置，Navicat 展示的 `aigc_canvas` 仅有旧 `t_*` 表；当前 Java 服务默认连接 `${MYSQL_DATABASE:semple_aigc_canvas}`。讨论加表时须先明确实际目标库，并区分资产/历史表与生成任务全链路依赖表。
- 2026-09-28：用户提供 `sql/semple_aigc_canvas.sql` 作为目标新库初始化脚本；该初始化文件已恢复为原内容，新增字段和索引单独放在 `sql/semple_aigc_canvas_output_index.sql` 增量脚本中。
- 2026-09-28：生成历史业务实现采用现有 `aigc_workflow_step_run.node_type` 作为任务运行时类型快照；创建任务校验节点类型与图片模型类型一致。成功结算仅写 `aigc_generated_asset`，按 `step_run_id + output_index` 通过数据库唯一键和 `INSERT IGNORE` 幂等更新，不再自动写 `asset`。
- 2026-09-28：模块结构核对结论：初始脚手架保留 auth、gateway、modules/system、modules/file、modules/job、modules/aigc 六个可部署入口；当前实际业务集中在 gateway 与 modules/aigc。system/file/job 仍在 Maven reactor 但各自只有启动类，system-api/file-api/job-api 也只有 package-info；顶层 auth 也只有启动类。认证、画布/项目/积分、模型生成及 worker 均已落在 AIGC 内；文件模块尚无业务实现，不能称为已迁入 AIGC。
- 2026-09-28：图片生成历史查询方案：在 AIGC 模块提供分页图片历史接口，按当前工作区读取 `aigc_generated_asset`，创建者名称关联 `user_account.username`，模型名称使用步骤运行快照；生成信息使用历史结果 URL、提示词和创建时间，选择参数读取 `generation_config`。当前请求配置仅是通用 JSON，没有固定的参考图字段规范，VO 只在明确的 `referenceImages` 键存在时返回参考图，不从其他字段猜测。
- 2026-09-29：确认当前生成历史链路：worker 定时扫描并租约领取本地任务；首次提交和后续轮询的供应商结果都进入 `applyProviderResult`。同步供应商 `submit` 直接返回成功，异步供应商由 `poll` 返回成功；成功处理先在事务内 `SELECT FOR UPDATE` 复核任务未终态及租约，再按 `step_run_id + output_index` 用数据库唯一键和 `INSERT IGNORE` 幂等写入 `aigc_generated_asset`，随后结算积分、标记任务成功并更新工作流。历史查询 `GET /generation/history/images` 返回 `R<Page<ImageGenerationHistoryVO>>`，其中 `Page.records` 是 VO 列表；当前接口仅查询图片历史。
- 2026-09-29：生成历史界面有两个独立维度：媒体类型筛选（全部/图片/视频/音频）和按创建日期分组展示。接口建议采用一个类型可选的扁平分页列表，按 `create_time` 倒序；返回稳定的类型、精确创建时间、结果地址/缩略图、媒体尺寸/时长、状态等字段，前端按日期分组。若类型 Tab 展示数量，另提供类型计数摘要或分页元数据，不把数据组织成日期嵌套树。
- 2026-09-29：按上述方案扩展现有图片历史查询：保留 `/generation/history/images` 兼容入口，新增 `/generation/history?type=ALL|IMAGE|VIDEO|AUDIO`；通用 VO 返回 assetType、地址/缩略图、MIME、宽高、时长及原有生成参数，查询按创建时间和 ID 倒序，日期分组仍由前端依据 createdAt 完成。

## 生成历史接口
- 2026-09-29：确认通用接口返回 `R<Page<GenerationHistoryVO>>`；`Page.records` 是扁平历史记录列表，不按日期嵌套，前端依据 `createdAt` 分组展示。
- 2026-09-29：图片、视频、音频历史共用创建者、创建时间、提示词、模型快照和生成配置；图片返回参考图，视频返回横竖比、分辨率及 `durationMs` 时长，音频返回 `referenceAudio`。请求配置中 Ark 视频的 `ratio` 与 `aspectRatio/aspect_ratio` 同等映射。

## 代码推送
- 2026-10-01：目标 GitHub 仓库 `https://github.com/jiancun666/jiangcun-aigc-canvas.git` 只有独立初始 README 提交，与本地 `dev` 历史无共同祖先；推送方案为保留远端 README 后合并历史，再将完整代码推送到目标 `main`，不强制覆盖。

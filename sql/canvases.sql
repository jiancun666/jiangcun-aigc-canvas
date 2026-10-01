-- 画布、模型与工作流模块建表脚本；数据库：MySQL 8.0+；字符集：utf8mb4。
-- 主键使用 MySQL 自增策略，统一从 1000 开始。
SET NAMES utf8mb4;
-- ============================================================
-- 二、项目画布、模型与工作流模块
-- ============================================================

-- 一个项目允许包含多个画布，项目表中的 canvas_id 指向默认画布。
DROP TABLE IF EXISTS `aigc_canvas`;
CREATE TABLE `aigc_canvas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '画布 ID',
  `project_item_id` BIGINT UNSIGNED NOT NULL COMMENT '所属项目 ID',
  `name` VARCHAR(40) NOT NULL DEFAULT '画布1' COMMENT '画布名称',
  `canvas_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，2已删除',
  `viewport_data` JSON NULL COMMENT '画布缩放、中心点等视口数据',
  `canvas_setting` JSON NULL COMMENT '画布级配置',
  `lock_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人用户 ID',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_status_created` (`project_item_id`, `canvas_status`, `create_time`),
  CONSTRAINT `chk_canvas_status` CHECK (`canvas_status` IN (1, 2))
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='项目画布';


-- 画布节点保存编辑态配置。运行时还会把配置复制到步骤执行表，避免后续编辑影响历史记录。
DROP TABLE IF EXISTS `aigc_canvas_node`;
CREATE TABLE `aigc_canvas_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '节点 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `node_key` VARCHAR(64) NOT NULL COMMENT '前端节点唯一键',
  `node_type` TINYINT UNSIGNED NOT NULL COMMENT '类型：1文本生成，2图片生成，3视频生成，4音频生成，5上传文件，6历史资产，7普通处理',
  `name` VARCHAR(100) NOT NULL COMMENT '节点名称',
  `model_definition_id` BIGINT UNSIGNED NULL COMMENT '所选模型定义 ID；非模型节点为空',
  `position_x` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '画布 X 坐标',
  `position_y` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '画布 Y 坐标',
  `width` DECIMAL(12,3) NULL COMMENT '节点宽度',
  `height` DECIMAL(12,3) NULL COMMENT '节点高度',
  `input_config` JSON NULL COMMENT '输入定义、提示词模板及上游字段映射',
  `model_config` JSON NULL COMMENT '模型参数，如尺寸、比例、时长、清晰度等',
  `node_data` JSON NULL COMMENT '节点其他编辑态数据',
  `node_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，2禁用，3已删除',
  `lock_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人用户 ID',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_canvas_node_key` (`canvas_id`, `node_key`),
  CONSTRAINT `chk_canvas_node_type` CHECK (`node_type` BETWEEN 1 AND 7),
  CONSTRAINT `chk_canvas_node_status` CHECK (`node_status` IN (1, 2, 3))
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='画布节点及编辑态配置';


-- 节点间的有向连接，用于生成工作流 DAG。
DROP TABLE IF EXISTS `aigc_canvas_edge`;
CREATE TABLE `aigc_canvas_edge` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '连线 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `edge_key` VARCHAR(64) NOT NULL COMMENT '前端连线唯一键',
  `source_node_id` BIGINT UNSIGNED NOT NULL COMMENT '来源节点 ID',
  `source_handle` VARCHAR(64) NULL COMMENT '来源节点输出端口',
  `target_node_id` BIGINT UNSIGNED NOT NULL COMMENT '目标节点 ID',
  `target_handle` VARCHAR(64) NULL COMMENT '目标节点输入端口',
  `edge_data` JSON NULL COMMENT '连线附加配置',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_canvas_edge_key` (`canvas_id`, `edge_key`),
  KEY `idx_canvas_source_target` (`canvas_id`, `source_node_id`, `target_node_id`),
  CONSTRAINT `chk_canvas_edge_nodes` CHECK (`source_node_id` <> `target_node_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='画布节点连线';


-- 模型接入定义。credential_ref 只保存密钥中心引用，不保存 API Key 明文。
DROP TABLE IF EXISTS `aigc_model_definition`;
CREATE TABLE `aigc_model_definition` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模型定义 ID',
  `provider_code` VARCHAR(50) NOT NULL COMMENT '供应商编码',
  `model_code` VARCHAR(100) NOT NULL COMMENT '模型编码',
  `model_name` VARCHAR(100) NOT NULL COMMENT '模型展示名称',
  `model_type` TINYINT UNSIGNED NOT NULL COMMENT '类型：1文本，2图片，3视频，4音频',
  `adapter_code` VARCHAR(100) NOT NULL COMMENT '服务端适配器编码',
  `endpoint_url` VARCHAR(500) NULL COMMENT '调用地址；使用适配器默认地址时可为空',
  `credential_ref` VARCHAR(200) NULL COMMENT '密钥中心或环境变量引用',
  `capability_config` JSON NULL COMMENT '支持的尺寸、比例、时长等能力',
  `default_request_config` JSON NULL COMMENT '默认请求参数',
  `timeout_seconds` INT UNSIGNED NOT NULL DEFAULT 300 COMMENT '单次调用超时秒数',
  `enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用：0否，1是',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_model` (`provider_code`, `model_code`),
  CONSTRAINT `chk_model_type` CHECK (`model_type` BETWEEN 1 AND 4),
  CONSTRAINT `chk_model_enabled` CHECK (`enabled` IN (0, 1)),
  CONSTRAINT `chk_model_timeout` CHECK (`timeout_seconds` > 0)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型供应商接入定义';


-- 模型积分价格按时间生效。rule_config 用于区分分辨率、时长、张数等计费条件。
DROP TABLE IF EXISTS `aigc_model_price_rule`;
CREATE TABLE `aigc_model_price_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模型计费规则 ID',
  `model_definition_id` BIGINT UNSIGNED NOT NULL COMMENT '模型定义 ID',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
  `billing_mode` TINYINT UNSIGNED NOT NULL COMMENT '计费方式：1按次，2按张，3按秒，4按Token，5组合规则',
  `unit_points` BIGINT UNSIGNED NOT NULL COMMENT '计费单位对应积分',
  `rule_config` JSON NULL COMMENT '计费匹配条件及阶梯规则',
  `effective_from` DATETIME(3) NOT NULL COMMENT '生效时间',
  `effective_to` DATETIME(3) NULL COMMENT '失效时间；为空表示长期有效',
  `enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用：0否，1是',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_effective` (`model_definition_id`, `enabled`, `effective_from`),
  CONSTRAINT `chk_model_price_mode` CHECK (`billing_mode` BETWEEN 1 AND 5),
  CONSTRAINT `chk_model_price_enabled` CHECK (`enabled` IN (0, 1)),
  CONSTRAINT `chk_model_price_points` CHECK (`unit_points` > 0),
  CONSTRAINT `chk_model_price_time`
      CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型积分计费规则';


-- 用户运行画布或部分节点时创建一条工作流运行记录。
DROP TABLE IF EXISTS `aigc_workflow_run`;
CREATE TABLE `aigc_workflow_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作流运行 ID',
  `run_no` VARCHAR(64) NOT NULL COMMENT '工作流运行编号',
  `project_item_id` BIGINT UNSIGNED NOT NULL COMMENT '项目 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `trigger_user_id` BIGINT UNSIGNED NOT NULL COMMENT '触发用户 ID',
  `point_account_id` BIGINT UNSIGNED NOT NULL COMMENT '本次运行使用的积分账户 ID',
  `trigger_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '触发方式：1手动运行，2重试，3接口调用',
  `run_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1等待，2运行中，3成功，4部分成功，5失败，6取消',
  `workflow_snapshot` JSON NOT NULL COMMENT '运行时节点、连线及配置完整快照',
  `estimated_points` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '运行前预计积分',
  `consumed_points` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实际累计消耗积分',
  `refunded_points` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实际累计返还积分',
  `total_steps` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总步骤数',
  `success_steps` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功步骤数',
  `failed_steps` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败步骤数',
  `started_at` DATETIME(3) NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_run_no` (`run_no`),
  KEY `idx_canvas_status_created` (`canvas_id`, `run_status`, `create_time` DESC),
  CONSTRAINT `chk_workflow_trigger_type` CHECK (`trigger_type` BETWEEN 1 AND 3),
  CONSTRAINT `chk_workflow_run_status` CHECK (`run_status` BETWEEN 1 AND 6)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='画布工作流运行记录';


-- 每执行一个节点产生一条步骤记录。模型请求、响应和计费快照均在此留存。
DROP TABLE IF EXISTS `aigc_workflow_step_run`;
CREATE TABLE `aigc_workflow_step_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '步骤执行 ID',
  `step_no` VARCHAR(64) NOT NULL COMMENT '步骤执行编号',
  `workflow_run_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流运行 ID',
  `canvas_node_id` BIGINT UNSIGNED NOT NULL COMMENT '画布节点 ID',
  `node_key` VARCHAR(64) NOT NULL COMMENT '运行时节点键快照',
  `node_type` TINYINT UNSIGNED NOT NULL COMMENT '运行时节点类型快照',
  `sequence_no` INT UNSIGNED NOT NULL COMMENT '工作流内执行序号',
  `attempt_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '该节点第几次尝试',
  `step_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1等待，2运行中，3成功，4失败，5取消，6跳过',
  `model_definition_id` BIGINT UNSIGNED NULL COMMENT '调用模型定义 ID',
  `provider_code` VARCHAR(50) NULL COMMENT '供应商编码快照',
  `model_code` VARCHAR(100) NULL COMMENT '模型编码快照',
  `model_name` VARCHAR(100) NULL COMMENT '模型名称快照',
  `provider_request_id` VARCHAR(200) NULL COMMENT '供应商请求 ID',
  `input_snapshot` JSON NULL COMMENT '解析上游节点后的实际输入',
  `request_payload` JSON NULL COMMENT '发往模型的请求参数；敏感字段应脱敏',
  `response_payload` JSON NULL COMMENT '模型原始响应或关键响应快照',
  `output_snapshot` JSON NULL COMMENT '标准化后的节点输出',
  `price_rule_id` BIGINT UNSIGNED NULL COMMENT '采用的计费规则 ID',
  `price_snapshot` JSON NULL COMMENT '计费规则及命中参数快照',
  `estimated_points` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '步骤预计积分',
  `consumed_points` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '步骤实际扣减积分',
  `refunded_points` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '步骤失败返还积分',
  `point_biz_order_id` BIGINT UNSIGNED NULL COMMENT '关联积分业务单 ID',
  `error_code` VARCHAR(100) NULL COMMENT '标准化错误编码',
  `error_message` VARCHAR(1000) NULL COMMENT '错误说明',
  `provider_error_data` JSON NULL COMMENT '供应商错误响应快照',
  `queued_at` DATETIME(3) NULL COMMENT '进入队列时间',
  `started_at` DATETIME(3) NULL COMMENT '开始执行时间',
  `finished_at` DATETIME(3) NULL COMMENT '完成时间',
  `duration_ms` BIGINT UNSIGNED NULL COMMENT '执行耗时毫秒',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_step_no` (`step_no`),
  UNIQUE KEY `uk_run_node_attempt` (`workflow_run_id`, `canvas_node_id`, `attempt_no`),
  UNIQUE KEY `uk_step_point_order` (`point_biz_order_id`),
  KEY `idx_run_status_sequence` (`workflow_run_id`, `step_status`, `sequence_no`),
  CONSTRAINT `chk_workflow_step_status` CHECK (`step_status` BETWEEN 1 AND 6),
  CONSTRAINT `chk_workflow_step_attempt` CHECK (`attempt_no` > 0)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工作流节点步骤执行及模型调用记录';


-- 保存模型生成或用户上传的最终数据。大文件存对象存储，表中保存地址及元数据。
DROP TABLE IF EXISTS `aigc_generated_asset`;
CREATE TABLE `aigc_generated_asset` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资产 ID',
  `asset_no` VARCHAR(64) NOT NULL COMMENT '资产编号',
  `asset_type` TINYINT UNSIGNED NOT NULL COMMENT '类型：1文本，2图片，3视频，4音频，5文件',
  `asset_source` TINYINT UNSIGNED NOT NULL COMMENT '来源：1模型生成，2用户上传，3历史资产引用',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '资产所属工作区 ID',
  `asset_id` BIGINT UNSIGNED NULL COMMENT '关联用户资产库 asset 表 ID',
  `project_item_id` BIGINT UNSIGNED NOT NULL COMMENT '项目 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `canvas_node_id` BIGINT UNSIGNED NOT NULL COMMENT '产生资产的节点 ID',
  `workflow_run_id` BIGINT UNSIGNED NULL COMMENT '工作流运行 ID',
  `step_run_id` BIGINT UNSIGNED NULL COMMENT '步骤执行 ID',
  `output_index` INT UNSIGNED NULL COMMENT '步骤内产出序号，从 1 开始；非步骤产出可为空',
  `creator_user_id` BIGINT UNSIGNED NOT NULL COMMENT '资产创建人用户 ID',
  `model_definition_id` BIGINT UNSIGNED NULL COMMENT '生成模型定义 ID',
  `storage_url` VARCHAR(1024) NULL COMMENT '对象存储地址；文本资产可为空',
  `thumbnail_url` VARCHAR(1024) NULL COMMENT '缩略图地址',
  `text_content` LONGTEXT NULL COMMENT '文本生成结果',
  `mime_type` VARCHAR(100) NULL COMMENT 'MIME 类型',
  `file_size` BIGINT UNSIGNED NULL COMMENT '文件字节数',
  `content_hash` VARCHAR(128) NULL COMMENT '文件内容摘要，用于完整性和去重',
  `width` INT UNSIGNED NULL COMMENT '图片/视频宽度',
  `height` INT UNSIGNED NULL COMMENT '图片/视频高度',
  `duration_ms` BIGINT UNSIGNED NULL COMMENT '视频/音频时长毫秒',
  `prompt_snapshot` LONGTEXT NULL COMMENT '生成时最终提示词',
  `generation_config` JSON NULL COMMENT '生成时模型参数快照',
  `asset_metadata` JSON NULL COMMENT '供应商及媒体扩展元数据',
  `asset_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，2处理中，3失效，4已删除',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generated_asset_no` (`asset_no`),
  UNIQUE KEY `uk_generated_asset_step_output` (`step_run_id`, `output_index`),
  KEY `idx_project_type_created` (`project_item_id`, `asset_type`, `create_time` DESC),
  KEY `idx_step_created` (`step_run_id`, `create_time`),
  CONSTRAINT `chk_asset_type` CHECK (`asset_type` BETWEEN 1 AND 5),
  CONSTRAINT `chk_asset_source` CHECK (`asset_source` BETWEEN 1 AND 3),
  CONSTRAINT `chk_asset_status` CHECK (`asset_status` BETWEEN 1 AND 4),
  CONSTRAINT `chk_asset_content`
      CHECK (`asset_status` = 2 OR `storage_url` IS NOT NULL OR `text_content` IS NOT NULL)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工作流生成及上传资产';


-- 单个模型调用对应一条生成任务；worker 通过租约字段避免多实例重复消费。
DROP TABLE IF EXISTS `aigc_generation_task`;
CREATE TABLE `aigc_generation_task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '生成任务 ID',
  `task_no` VARCHAR(64) NOT NULL COMMENT '对外任务编号',
  `client_request_id` VARCHAR(128) NOT NULL COMMENT '客户端幂等请求号',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '扣费工作区 ID',
  `creator_user_id` BIGINT UNSIGNED NOT NULL COMMENT '任务创建用户 ID',
  `project_item_id` BIGINT UNSIGNED NOT NULL COMMENT '项目 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `canvas_node_id` BIGINT UNSIGNED NOT NULL COMMENT '执行节点 ID',
  `workflow_run_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流运行 ID',
  `workflow_step_run_id` BIGINT UNSIGNED NOT NULL COMMENT '步骤运行 ID',
  `model_definition_id` BIGINT UNSIGNED NOT NULL COMMENT '模型定义 ID',
  `price_rule_id` BIGINT UNSIGNED NOT NULL COMMENT '价格规则 ID',
  `point_biz_order_id` BIGINT UNSIGNED NULL COMMENT '积分业务单 ID',
  `retry_of_task_id` BIGINT UNSIGNED NULL COMMENT '重试来源任务 ID',
  `task_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1等待，2提交中，3供应商处理中，4成功，5失败，6取消',
  `progress` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '进度百分比',
  `attempt_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'worker 尝试次数',
  `provider_request_id` VARCHAR(200) NULL COMMENT '供应商任务 ID',
  `prompt` LONGTEXT NOT NULL COMMENT '最终提示词快照',
  `request_config` JSON NULL COMMENT '模型请求参数快照',
  `result_payload` JSON NULL COMMENT '标准化结果快照',
  `error_code` VARCHAR(100) NULL COMMENT '标准化错误码',
  `error_message` VARCHAR(1000) NULL COMMENT '错误说明',
  `cancel_requested` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否请求取消：0否，1是',
  `lock_owner` VARCHAR(100) NULL COMMENT '当前 worker 标识',
  `lease_until` DATETIME(3) NULL COMMENT 'worker 租约到期时间',
  `next_poll_at` DATETIME(3) NULL COMMENT '下次轮询时间',
  `expires_at` DATETIME(3) NOT NULL COMMENT '任务超时时间',
  `started_at` DATETIME(3) NULL COMMENT '开始执行时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generation_task_no` (`task_no`),
  UNIQUE KEY `uk_workspace_client_request` (`workspace_id`, `client_request_id`),
  KEY `idx_generation_task_dispatch` (`task_status`, `next_poll_at`, `lease_until`),
  KEY `idx_generation_task_project` (`project_item_id`, `create_time` DESC),
  CONSTRAINT `chk_generation_task_status` CHECK (`task_status` BETWEEN 1 AND 6),
  CONSTRAINT `chk_generation_task_progress` CHECK (`progress` BETWEEN 0 AND 100),
  CONSTRAINT `chk_generation_task_cancel` CHECK (`cancel_requested` IN (0, 1))
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='异步模型生成任务';


-- 每次向供应商提交、查询或取消都写日志，密钥和 Authorization 不得入库。
DROP TABLE IF EXISTS `aigc_model_call_log`;
CREATE TABLE `aigc_model_call_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '调用日志 ID',
  `generation_task_id` BIGINT UNSIGNED NOT NULL COMMENT '生成任务 ID',
  `attempt_no` INT UNSIGNED NOT NULL COMMENT '调用尝试序号',
  `action_type` TINYINT UNSIGNED NOT NULL COMMENT '动作：1提交，2查询，3取消',
  `provider_code` VARCHAR(50) NOT NULL COMMENT '供应商编码',
  `model_code` VARCHAR(100) NOT NULL COMMENT '模型编码',
  `provider_request_id` VARCHAR(200) NULL COMMENT '供应商请求 ID',
  `call_status` TINYINT UNSIGNED NOT NULL COMMENT '结果：1成功，2失败',
  `request_payload` JSON NULL COMMENT '脱敏请求快照',
  `response_payload` JSON NULL COMMENT '脱敏响应快照',
  `usage_snapshot` JSON NULL COMMENT '实际用量快照',
  `error_code` VARCHAR(100) NULL COMMENT '错误码',
  `error_message` VARCHAR(1000) NULL COMMENT '错误说明',
  `started_at` DATETIME(3) NOT NULL COMMENT '调用开始时间',
  `finished_at` DATETIME(3) NOT NULL COMMENT '调用结束时间',
  `duration_ms` BIGINT UNSIGNED NOT NULL COMMENT '调用耗时毫秒',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_call_task_time` (`generation_task_id`, `create_time`),
  CONSTRAINT `chk_model_call_action` CHECK (`action_type` BETWEEN 1 AND 3),
  CONSTRAINT `chk_model_call_status` CHECK (`call_status` IN (1, 2))
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型供应商调用审计日志';



-- 画布多节点执行快照，记录一次有向无环图运行的整体状态。
DROP TABLE IF EXISTS `aigc_canvas_workflow_execution_step`;
DROP TABLE IF EXISTS `aigc_canvas_workflow_execution`;
CREATE TABLE `aigc_canvas_workflow_execution` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '画布执行 ID',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作区 ID',
  `project_item_id` BIGINT UNSIGNED NOT NULL COMMENT '所属项目 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `creator_user_id` BIGINT UNSIGNED NOT NULL COMMENT '发起用户 ID',
  `canvas_revision` INT UNSIGNED NOT NULL COMMENT '发起时画布版本',
  `target_node_key` VARCHAR(64) NOT NULL COMMENT '目标节点键',
  `execution_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1运行中，2成功，3部分成功，4失败',
  `error_message` VARCHAR(500) NULL COMMENT '错误说明',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_canvas_execution` (`canvas_id`, `create_time` DESC),
  KEY `idx_execution_status` (`execution_status`, `update_time`),
  CONSTRAINT `chk_canvas_execution_status` CHECK (`execution_status` BETWEEN 1 AND 4)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='画布多节点执行';

-- 每个节点保存独立输入、依赖和输出快照，供调度器按连线顺序推进。
CREATE TABLE `aigc_canvas_workflow_execution_step` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '执行步骤 ID',
  `execution_id` BIGINT UNSIGNED NOT NULL COMMENT '画布执行 ID',
  `canvas_node_id` BIGINT UNSIGNED NOT NULL COMMENT '画布节点 ID',
  `node_key` VARCHAR(64) NOT NULL COMMENT '节点键快照',
  `node_type` TINYINT UNSIGNED NOT NULL COMMENT '节点类型快照',
  `sequence_no` INT UNSIGNED NOT NULL COMMENT '拓扑排序序号',
  `model_definition_id` BIGINT UNSIGNED NULL COMMENT '模型定义 ID',
  `prompt` TEXT NULL COMMENT '提示词快照',
  `model_config` JSON NULL COMMENT '模型参数快照',
  `dependencies` JSON NOT NULL COMMENT '上游节点键列表',
  `task_no` VARCHAR(64) NULL COMMENT '模型生成任务编号',
  `output_snapshot` JSON NULL COMMENT '上游素材或生成结果快照',
  `step_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1等待，2已提交，3成功，4失败，5跳过',
  `error_message` VARCHAR(500) NULL COMMENT '错误说明',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_node` (`execution_id`, `node_key`),
  UNIQUE KEY `uk_execution_sequence` (`execution_id`, `sequence_no`),
  KEY `idx_execution_step_status` (`execution_id`, `step_status`),
  CONSTRAINT `chk_canvas_execution_step_status` CHECK (`step_status` BETWEEN 1 AND 5)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='画布执行节点快照';

-- 画布每次保存或生成结果回写时，按版本号保存完整内容。
DROP TABLE IF EXISTS `aigc_canvas_version`;
CREATE TABLE `aigc_canvas_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '画布版本 ID',
  `canvas_id` BIGINT UNSIGNED NOT NULL COMMENT '画布 ID',
  `revision` INT UNSIGNED NOT NULL COMMENT '画布版本号',
  `snapshot_json` JSON NOT NULL COMMENT '画布完整内容快照',
  `created_by_user_id` BIGINT UNSIGNED NOT NULL COMMENT '版本创建用户 ID',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_canvas_revision` (`canvas_id`, `revision`),
  KEY `idx_canvas_version_time` (`canvas_id`, `create_time` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='画布版本快照';


-- 实现约束：
-- 1. 保存画布时锁定 aigc_canvas，并校验 lock_version；节点按 node_key 差量更新以保持数据库 ID 稳定。
-- 2. 工作流启动时冻结 workflow_snapshot；模型配置与价格规则复制到步骤快照。
-- 3. request_payload 不得保存密钥、Authorization 或签名 URL，大文件写对象存储。


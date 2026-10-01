-- 积分模块建表脚本；数据库：MySQL 8.0+；字符集：utf8mb4。
-- 主键使用 MySQL 自增策略，统一从 1000 开始。
SET NAMES utf8mb4;
-- ============================================================
-- 三、积分模块
-- ============================================================

-- 每个个人或团队工作区拥有一个积分账户。
DROP TABLE IF EXISTS `aigc_point_account`;
CREATE TABLE `aigc_point_account` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '积分账户 ID',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '积分所属个人或团队工作区 ID',
  `available_balance` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前可用积分',
  `reserved_balance` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '生成任务已预占、尚未结算的积分',
  `acquired_total` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计获取积分',
  `consumed_total` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计消耗积分',
  `refunded_total` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计返还积分',
  `cleared_total` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '团队解散等场景累计清零积分',
  `account_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，2冻结，3已关闭',
  `lock_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，用于并发更新',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace` (`workspace_id`),
  CONSTRAINT `chk_point_account_status`
      CHECK (`account_status` IN (1, 2, 3))
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='个人及团队积分账户';


-- 积分流水为不可变记录；修正数据时写一条反向流水，不直接修改历史流水。
-- change_amount：增加为正数，扣减/清零为负数。
DROP TABLE IF EXISTS `aigc_point_ledger`;
CREATE TABLE `aigc_point_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '积分流水 ID',
  `account_id` BIGINT UNSIGNED NOT NULL COMMENT '积分账户 ID',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '积分所属个人或团队工作区 ID',
  `ledger_type` TINYINT UNSIGNED NOT NULL COMMENT '流水类型：1系统发放，2生成预占，3生成结算，4预占释放，5团队解散清零，6人工调整',
  `change_amount` BIGINT NOT NULL COMMENT '可用积分变动：增加为正，预占/清零为负',
  `balance_before` BIGINT UNSIGNED NOT NULL COMMENT '变动前余额',
  `balance_after` BIGINT UNSIGNED NOT NULL COMMENT '变动后余额',
  `reserved_change` BIGINT NOT NULL DEFAULT 0 COMMENT '预占积分变动：预占为正，结算或释放为负',
  `reserved_before` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '变动前预占余额',
  `reserved_after` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '变动后预占余额',
  `operator_user_id` BIGINT UNSIGNED NULL COMMENT '操作人；系统自动处理时为空',
  `member_user_id` BIGINT UNSIGNED NULL COMMENT '团队积分实际使用成员；个人账户可为空',
  `project_item_id` BIGINT UNSIGNED NULL COMMENT '关联项目 ID',
  `workflow_run_id` BIGINT UNSIGNED NULL COMMENT '关联工作流运行 ID',
  `workflow_step_run_id` BIGINT UNSIGNED NULL COMMENT '关联步骤执行 ID',
  `model_code` VARCHAR(100) NULL COMMENT '生成模型编码',
  `generation_task_id` VARCHAR(128) NULL COMMENT '生成任务 ID',
  `biz_order_no` VARCHAR(64) NULL COMMENT '积分业务单号',
  `related_ledger_id` BIGINT UNSIGNED NULL COMMENT '关联原流水，例如返还关联消耗流水',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键；同一业务动作全局唯一',
  `remark` VARCHAR(500) NULL COMMENT '流水备注',
  `extra_data` JSON NULL COMMENT '扩展业务数据',
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务发生时间',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_point_ledger_idempotency` (`idempotency_key`),
  KEY `idx_account_type_time` (`account_id`, `ledger_type`, `occurred_at` DESC),
  KEY `idx_account_member_type_time`
      (`account_id`, `member_user_id`, `ledger_type`, `occurred_at` DESC),
  CONSTRAINT `chk_point_ledger_type`
      CHECK (`ledger_type` BETWEEN 1 AND 6),
  CONSTRAINT `chk_point_ledger_change_amount`
      CHECK (`change_amount` <> 0 OR `reserved_change` <> 0),
  CONSTRAINT `chk_point_ledger_balance`
      CHECK (`balance_after` = `balance_before` + `change_amount`),
  CONSTRAINT `chk_point_ledger_reserved_balance`
      CHECK (`reserved_after` = `reserved_before` + `reserved_change`)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='积分不可变流水';


-- 生成任务的积分扣减/返还业务单。
-- 该表用于将重复提交、重复失败回调收敛到同一业务单。
DROP TABLE IF EXISTS `aigc_point_biz_order`;
CREATE TABLE `aigc_point_biz_order` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '积分业务单 ID',
  `biz_order_no` VARCHAR(64) NOT NULL COMMENT '积分业务单号',
  `generation_task_id` VARCHAR(128) NOT NULL COMMENT '生成任务 ID',
  `account_id` BIGINT UNSIGNED NOT NULL COMMENT '扣费积分账户 ID',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '积分所属个人或团队工作区 ID',
  `member_user_id` BIGINT UNSIGNED NOT NULL COMMENT '发起生成任务的用户 ID',
  `project_item_id` BIGINT UNSIGNED NULL COMMENT '所属项目 ID',
  `workflow_run_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流运行 ID',
  `workflow_step_run_id` BIGINT UNSIGNED NOT NULL COMMENT '步骤执行 ID',
  `model_definition_id` BIGINT UNSIGNED NOT NULL COMMENT '模型定义 ID',
  `price_rule_id` BIGINT UNSIGNED NOT NULL COMMENT '命中的价格规则 ID',
  `model_code` VARCHAR(100) NOT NULL COMMENT '模型编码快照',
  `cost_points` BIGINT UNSIGNED NOT NULL COMMENT '任务创建时预占积分',
  `actual_points` BIGINT UNSIGNED NULL COMMENT '任务完成后实际结算积分',
  `price_snapshot` JSON NOT NULL COMMENT '创建订单时冻结的计费规则及命中参数',
  `usage_snapshot` JSON NULL COMMENT '供应商返回的实际用量快照',
  `order_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1已预占，2运行中，3已结算，4已退款，5结算不确定，6关闭',
  `consume_ledger_id` BIGINT UNSIGNED NULL COMMENT '消耗流水 ID',
  `refund_ledger_id` BIGINT UNSIGNED NULL COMMENT '返还流水 ID',
  `failure_code` VARCHAR(64) NULL COMMENT '生成失败编码',
  `failure_message` VARCHAR(500) NULL COMMENT '生成失败说明',
  `charged_at` DATETIME(3) NULL COMMENT '扣减时间',
  `finished_at` DATETIME(3) NULL COMMENT '生成任务完成时间',
  `refunded_at` DATETIME(3) NULL COMMENT '返还时间',
  `lock_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_point_biz_order_no` (`biz_order_no`),
  UNIQUE KEY `uk_point_generation_task` (`generation_task_id`),
  UNIQUE KEY `uk_point_workflow_step` (`workflow_step_run_id`),
  UNIQUE KEY `uk_consume_ledger` (`consume_ledger_id`),
  UNIQUE KEY `uk_refund_ledger` (`refund_ledger_id`),
  KEY `idx_account_status_created` (`account_id`, `order_status`, `create_time` DESC),
  CONSTRAINT `chk_point_order_status`
      CHECK (`order_status` BETWEEN 1 AND 6),
  CONSTRAINT `chk_point_order_cost`
      CHECK (`cost_points` > 0)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='生成任务积分扣减及返还业务单';



-- 实现约束：
-- 1. 余额变更先锁定 aigc_point_account，再写不可变流水和业务单。
-- 2. generation_task_id、workflow_step_run_id 和 idempotency_key 用于防止重复扣费或退款。
-- 3. 退款与扣费使用相同的“账户 -> 业务单”锁顺序，降低并发死锁风险。
-- 4. 团队解散时清零余额、写清零流水并关闭账户。


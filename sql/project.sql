-- 项目与文件夹模块建表脚本；数据库：MySQL 8.0+；字符集：utf8mb4。
-- 主键使用 MySQL 自增策略，统一从 1000 开始。
SET NAMES utf8mb4;
-- ============================================================
-- 一、项目模块
-- ============================================================

-- 文件夹和项目使用同一张表：
-- item_type=1 为文件夹；item_type=2 为项目。
-- workspace_id 统一指向个人或团队工作区。
DROP TABLE IF EXISTS `aigc_project_item`;
CREATE TABLE `aigc_project_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目项 ID',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '所属个人或团队工作区 ID',
  `item_type` TINYINT UNSIGNED NOT NULL COMMENT '项目项类型：1文件夹，2项目',
  `parent_id` BIGINT UNSIGNED NULL COMMENT '当前所在文件夹 ID；根目录为空',
  `original_parent_id` BIGINT UNSIGNED NULL COMMENT '进入回收站前的父文件夹 ID，用于恢复',
  `creator_user_id` BIGINT UNSIGNED NOT NULL COMMENT '创建人用户 ID',
  `name` VARCHAR(40) NOT NULL COMMENT '名称，应用层限制最多 10 个 Unicode 字符',
  `cover_url` VARCHAR(1024) NULL COMMENT '封面图片地址',
  `canvas_id` BIGINT UNSIGNED NULL COMMENT '项目默认画布 ID；文件夹为空',
  `item_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，2回收站',
  `deleted_by` BIGINT UNSIGNED NULL COMMENT '执行删除的用户 ID',
  `deleted_at` DATETIME(3) NULL COMMENT '进入回收站时间',
  `purge_at` DATETIME(3) NULL COMMENT '预计永久清理时间，默认 deleted_at + 30 天',
  `lock_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_space_parent_status_created`
      (`workspace_id`, `parent_id`, `item_status`, `create_time` DESC),
  KEY `idx_recycle_purge` (`item_status`, `purge_at`),
  CONSTRAINT `chk_project_item_type`
      CHECK (`item_type` IN (1, 2)),
  CONSTRAINT `chk_project_item_status`
      CHECK (`item_status` IN (1, 2)),
  CONSTRAINT `chk_project_item_canvas`
      CHECK ((`item_type` = 1 AND `canvas_id` IS NULL) OR `item_type` = 2),
  CONSTRAINT `chk_project_item_recycle_time`
      CHECK (
        (`item_status` = 1 AND `deleted_at` IS NULL AND `purge_at` IS NULL)
        OR
        (`item_status` = 2 AND `deleted_at` IS NOT NULL AND `purge_at` IS NOT NULL)
      )
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='个人及团队的文件夹/项目';


-- 项目/文件夹操作日志。
-- 团队负责人操作其他成员的数据时，也必须写入此表。
DROP TABLE IF EXISTS `aigc_project_operation_log`;
CREATE TABLE `aigc_project_operation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志 ID',
  `project_item_id` BIGINT UNSIGNED NOT NULL COMMENT '项目项 ID',
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '所属个人或团队工作区 ID',
  `item_type` TINYINT UNSIGNED NOT NULL COMMENT '项目项类型：1文件夹，2项目',
  `operator_user_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人用户 ID',
  `creator_user_id` BIGINT UNSIGNED NOT NULL COMMENT '项目项创建人用户 ID',
  `operation_type` TINYINT UNSIGNED NOT NULL COMMENT '操作：1创建，2打开，3重命名，4更换封面，5移入文件夹，6移出文件夹，7删除到回收站，8恢复，9永久清理，10切换默认画布',
  `before_data` JSON NULL COMMENT '变更前数据快照',
  `after_data` JSON NULL COMMENT '变更后数据快照',
  `request_id` VARCHAR(64) NULL COMMENT '请求链路 ID',
  `deleted` INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
  `create_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_item_created` (`project_item_id`, `create_time` DESC),
  CONSTRAINT `chk_project_log_item_type`
      CHECK (`item_type` IN (1, 2)),
  CONSTRAINT `chk_project_log_operation_type`
      CHECK (`operation_type` BETWEEN 1 AND 10)
) ENGINE=InnoDB AUTO_INCREMENT=1000
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='项目及文件夹操作审计日志';



-- 实现约束：
-- 1. 名称最多 10 个 Unicode 字符，由应用层校验。
-- 2. 删除时记录 original_parent_id，并设置 purge_at；恢复时原目录失效则回根目录。
-- 3. 团队管理员可管理全团队数据，普通成员仅能修改自己创建的数据。
-- 4. 定时任务按 idx_recycle_purge 分批永久清理，操作日志在物理删除前写入。


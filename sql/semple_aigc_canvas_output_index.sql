-- MySQL 8.0；对已存在的 aigc_generated_asset 执行一次。
ALTER TABLE `aigc_generated_asset`
ADD COLUMN `output_index` INT UNSIGNED NULL
COMMENT '步骤内产出序号，从 1 开始；非步骤产出可为空'
AFTER `step_run_id`;

ALTER TABLE `aigc_generated_asset`
ADD UNIQUE KEY `uk_generated_asset_step_output`
(`step_run_id`, `output_index`);

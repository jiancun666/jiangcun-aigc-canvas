-- 一期必接模型目录（来源：MCN 画布一期功能调研表）。
-- 原厂密钥通过 DASHSCOPE_API_KEY、ARK_API_KEY、KLING_ACCESS_KEY、KLING_SECRET_KEY、
-- MINIMAX_API_KEY、LIBTV_ACCESS_KEY 环境变量注入，禁止在数据库中保存明文。
-- 积分按接口成本一比一换算：100 积分 = 1 元人民币，不包含利润加价。
-- 人民币接口直接换算；美元接口按 2026-09-28 USD/CNY=6.7131 冻结汇率并随价格规则版本留痕。
-- 动态模型以供应商返回的 Token、字符或视频时长结算，所有不足 1 积分的成本向上取整为 1 积分。

-- 兼容曾执行过“全部经 LibTV”旧脚本的环境：保留原记录 ID，仅纠正原厂与适配器归属。
UPDATE `aigc_model_definition` SET `provider_code` = 'ALIBABA' WHERE `model_code` = 'M004' AND `provider_code` = 'LIBTV';
UPDATE `aigc_model_definition` SET `provider_code` = 'BYTEDANCE' WHERE `model_code` IN ('M010','M024','M025') AND `provider_code` = 'LIBTV';
UPDATE `aigc_model_definition` SET `provider_code` = 'MINIMAX' WHERE `model_code` IN ('M058','M059') AND `provider_code` IN ('LIBTV','BYTEDANCE');
UPDATE `aigc_model_definition` SET `provider_code` = 'KUAISHOU' WHERE `model_code` = 'M034' AND `provider_code` = 'LIBTV';

INSERT INTO `aigc_model_definition`
(`provider_code`, `model_code`, `model_name`, `model_type`, `adapter_code`, `endpoint_url`,
 `credential_ref`, `capability_config`, `default_request_config`, `timeout_seconds`, `enabled`)
VALUES
('ALIBABA', 'M004', 'Qwen 3 VL Flash', 1, 'DASHSCOPE_CHAT', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'env:DASHSCOPE_API_KEY',
 '{"phase":1,"input":["text","image","video"],"output":["text"]}',
 '{"providerModelName":"qwen3-vl-flash","enable_thinking":false}', 300, 1),
('LIBTV', 'M005', 'Lib Image 2.5 Pro', 2, 'LIBTV', 'https://im.liblib.tv', 'env:LIBTV_ACCESS_KEY',
 '{"phase":1,"output":["image"]}', '{"providerModelName":"Lib Image 2.5 Pro"}', 900, 1),
('LIBTV', 'M006', 'Lib Image 2.5 Fast', 2, 'LIBTV', 'https://im.liblib.tv', 'env:LIBTV_ACCESS_KEY',
 '{"phase":1,"output":["image"]}', '{"providerModelName":"Lib Image 2.5 Fast"}', 900, 1),
('LIBTV', 'M007', 'Lib Image', 2, 'LIBTV', 'https://im.liblib.tv', 'env:LIBTV_ACCESS_KEY',
 '{"phase":1,"output":["image"]}', '{"providerModelName":"Lib Image"}', 900, 1),
('LIBTV', 'M008', 'General image Pro', 2, 'LIBTV', 'https://im.liblib.tv', 'env:LIBTV_ACCESS_KEY',
 '{"phase":1,"output":["image"]}', '{"providerModelName":"General image Pro"}', 900, 1),
('LIBTV', 'M009', 'General image V2', 2, 'LIBTV', 'https://im.liblib.tv', 'env:LIBTV_ACCESS_KEY',
 '{"phase":1,"output":["image"]}', '{"providerModelName":"General image V2"}', 900, 1),
('BYTEDANCE', 'M010', 'Seedream 5.0 Pro', 2, 'ARK_IMAGE', 'https://ark.cn-beijing.volces.com/api/v3', 'env:ARK_API_KEY',
 '{"phase":1,"output":["image"],"originalVendor":"ByteDance Seed"}',
 '{"providerModelName":"doubao-seedream-5-0-pro-260628","size":"2K","watermark":false}', 900, 1),
('LIBTV', 'M012', 'Style Image V8.2', 2, 'LIBTV', 'https://im.liblib.tv', 'env:LIBTV_ACCESS_KEY',
 '{"phase":1,"output":["image"]}', '{"providerModelName":"Style Image V8.2"}', 900, 1),
('BYTEDANCE', 'M024', 'Seedance 2.5', 3, 'ARK_VIDEO', 'https://ark.cn-beijing.volces.com/api/v3', 'env:ARK_API_KEY',
 '{"phase":1,"output":["video"],"originalVendor":"ByteDance Seed"}',
 '{"providerModelName":"doubao-seedance-2-5-260628","resolution":"720p","ratio":"16:9","duration":5,"generate_audio":true,"watermark":false}', 1800, 1),
('BYTEDANCE', 'M025', 'Seedance 2.0 VIP', 3, 'ARK_VIDEO', 'https://ark.cn-beijing.volces.com/api/v3', 'env:ARK_API_KEY',
 '{"phase":1,"output":["video"],"originalVendor":"ByteDance Seed","sourceAlias":"Seedance 2.0 VIP","directEquivalent":"Seedance 2.0 online"}',
 '{"providerModelName":"doubao-seedance-2-0-260128","resolution":"720p","ratio":"16:9","duration":5,"generate_audio":true,"service_tier":"default","watermark":false}', 1800, 1),
('KUAISHOU', 'M034', 'Kling O3', 3, 'KLING_VIDEO', 'https://api.klingai.com', 'env:KLING_ACCESS_KEY',
 '{"phase":1,"output":["video"],"originalVendor":"Kuaishou Kling","officialName":"Kling 3.0 Omni"}',
 '{"providerModelName":"kling-v3-omni","apiPath":"/v1/videos/omni-video","mode":"pro","duration":"5","aspect_ratio":"16:9","sound":"on"}', 1800, 1),
('MINIMAX', 'M058', 'MiniMax Speech 2.8 HD', 4, 'MINIMAX_SPEECH', 'https://api.minimax.io', 'env:MINIMAX_API_KEY',
 '{"phase":1,"input":["text"],"output":["audio"],"originalVendor":"MiniMax","mode":"text-to-speech","formats":["mp3","wav","flac"]}',
 '{"providerModelName":"speech-2.8-hd","language_boost":"auto","voice_setting":{"voice_id":"Chinese (Mandarin)_Lyrical_Voice","speed":1.0,"vol":1.0,"pitch":0},"audio_setting":{"sample_rate":32000,"bitrate":128000,"format":"mp3","channel":1}}', 300, 1),
('MINIMAX', 'M059', 'MiniMax Speech 2.8 Turbo', 4, 'MINIMAX_SPEECH', 'https://api.minimax.io', 'env:MINIMAX_API_KEY',
 '{"phase":1,"input":["text"],"output":["audio"],"originalVendor":"MiniMax","mode":"text-to-speech","formats":["mp3","wav","flac"]}',
 '{"providerModelName":"speech-2.8-turbo","language_boost":"auto","voice_setting":{"voice_id":"Chinese (Mandarin)_Lyrical_Voice","speed":1.0,"vol":1.0,"pitch":0},"audio_setting":{"sample_rate":32000,"bitrate":128000,"format":"mp3","channel":1}}', 300, 1)
ON DUPLICATE KEY UPDATE
 `model_name` = VALUES(`model_name`),
 `model_type` = VALUES(`model_type`),
 `adapter_code` = VALUES(`adapter_code`),
 `endpoint_url` = VALUES(`endpoint_url`),
 `credential_ref` = VALUES(`credential_ref`),
 `capability_config` = VALUES(`capability_config`),
 `default_request_config` = VALUES(`default_request_config`),
 `timeout_seconds` = VALUES(`timeout_seconds`),
 `enabled` = VALUES(`enabled`);

-- 停用旧版 1 积分占位规则，防止它因生效时间更晚而覆盖真实成本规则。
UPDATE `aigc_model_price_rule` r
JOIN `aigc_model_definition` m ON m.id = r.model_definition_id
SET r.enabled = 0,
    r.effective_to = COALESCE(r.effective_to, CURRENT_TIMESTAMP(3))
WHERE m.model_code IN ('M004','M005','M006','M007','M008','M009','M010','M012','M024','M025','M034','M058','M059')
  AND r.rule_name = '一期默认按次计费'
  AND r.enabled = 1;

-- 原厂成本价：unit_points 与 rule_config 共同表达供应商刊例价，结算时统一向上取整。
-- M004：输入 Token 分档后，同一档位的输入、输出分别计费；最多预占 36 积分，完成后多退少补。
-- M010：默认 2K 输出按大于 236 万像素档计 0.90 元/张，即 90 积分/张。
-- M024/M025：默认 720p、无视频输入，分别按 70/69 元每百万输出 Token 结算。
-- M034：原生音频 1080p 为 12 Credits/秒，按 66 Credits=1 USD 及冻结汇率换算。
-- M058/M059：当前接入 api.minimax.io，分别按 100/60 USD 每百万字符结算。
INSERT INTO `aigc_model_price_rule`
(`model_definition_id`, `rule_name`, `billing_mode`, `unit_points`, `rule_config`, `effective_from`, `enabled`)
SELECT m.id, p.rule_name, p.billing_mode, p.unit_points, p.rule_config, TIMESTAMP('2026-09-28 00:00:00.000'), 1
FROM `aigc_model_definition` m
JOIN (
  SELECT 'M004' model_code, '原厂成本价-2026-09-28' rule_name, 5 billing_mode, 1 unit_points,
         '{"currency":"CNY","pointsPerCny":100,"calculation":"TIERED_TOKEN","estimatedPoints":36,"tiers":[{"maxInputTokens":32768,"inputPointsPerMillion":15,"outputPointsPerMillion":150},{"maxInputTokens":131072,"inputPointsPerMillion":30,"outputPointsPerMillion":300},{"maxInputTokens":262144,"inputPointsPerMillion":60,"outputPointsPerMillion":600}],"source":"Alibaba Cloud Model Studio qwen3-vl-flash list price"}' rule_config
  UNION ALL SELECT 'M010', '原厂成本价-2026-09-28', 2, 90,
         '{"currency":"CNY","pointsPerCny":100,"priceCnyPerImage":0.9,"condition":{"size":"2K","pixels":">2360000"},"source":"Volcengine Ark Seedream 5.0 Pro list price"}'
  UNION ALL SELECT 'M024', '原厂成本价-2026-09-28', 4, 7000,
         '{"currency":"CNY","pointsPerCny":100,"usageKey":"completionTokens","unitSize":1000000,"estimatedPoints":756,"condition":{"resolution":"720p","inputContainsVideo":false},"source":"Volcengine Ark Seedance 2.5 list price"}'
  UNION ALL SELECT 'M025', '原厂成本价-2026-09-28', 4, 6900,
         '{"currency":"CNY","pointsPerCny":100,"usageKey":"completionTokens","unitSize":1000000,"estimatedPoints":497,"condition":{"resolution":"720p","inputContainsVideo":false},"source":"Volcengine Ark Seedance 2.0 list price"}'
  UNION ALL SELECT 'M034', '原厂成本价-2026-09-28', 4, 805572,
         '{"currency":"USD","pointsPerCny":100,"usdCny":6.7131,"usageKey":"durationMs","unitSize":6600000,"estimatedPoints":611,"creditsPerSecond":12,"creditsPerUsd":66,"condition":{"resolution":"1080p","sound":"on"},"source":"Kling 3.0 Native Audio credits list price"}'
  UNION ALL SELECT 'M058', '原厂成本价-2026-09-28', 4, 671310,
         '{"currency":"USD","pointsPerCny":100,"usdCny":6.7131,"usageKey":"characters","unitSize":10000000,"usdPerMillionCharacters":100,"source":"MiniMax global pay-as-you-go list price"}'
  UNION ALL SELECT 'M059', '原厂成本价-2026-09-28', 4, 402786,
         '{"currency":"USD","pointsPerCny":100,"usdCny":6.7131,"usageKey":"characters","unitSize":10000000,"usdPerMillionCharacters":60,"source":"MiniMax global pay-as-you-go list price"}'
) p ON p.model_code = m.model_code
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_model_price_rule` r
  WHERE r.model_definition_id = m.id AND r.rule_name = p.rule_name
);

-- M005/M006/M007/M008/M009/M012 由 LibTV 会话接口调用。其公开 OpenAPI 不返回单次消费金额，
-- 且公开价格页未披露这些目录别名的固定单价，因此不写入虚假成本规则；取得商务账单单价后再补充。

package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.mapper.GeneratedAssetParentMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 生成资产数据访问接口。
 */
@Mapper
public interface GeneratedAssetMapper extends GeneratedAssetParentMapper {
    @Select("SELECT * FROM aigc_generated_asset WHERE id=#{id} AND deleted=1 FOR UPDATE")
    GeneratedAsset selectForUpdate(Long id);

    /**
     * 依赖数据库唯一键处理并发重复结果；重复产出不会抛出异常破坏任务结算事务。
     */
    @Insert("INSERT IGNORE INTO aigc_generated_asset "
            + "(asset_no, asset_type, asset_source, workspace_id, asset_id, project_item_id, canvas_id, "
            + "canvas_node_id, workflow_run_id, step_run_id, output_index, creator_user_id, model_definition_id, "
            + "storage_url, thumbnail_url, text_content, mime_type, file_size, content_hash, width, height, "
            + "duration_ms, prompt_snapshot, generation_config, asset_metadata, asset_status) "
            + "VALUES (#{assetNo}, #{assetType}, #{assetSource}, #{workspaceId}, #{assetId}, #{projectItemId}, "
            + "#{canvasId}, #{canvasNodeId}, #{workflowRunId}, #{stepRunId}, #{outputIndex}, #{creatorUserId}, "
            + "#{modelDefinitionId}, #{storageUrl}, #{thumbnailUrl}, #{textContent}, #{mimeType}, #{fileSize}, "
            + "#{contentHash}, #{width}, #{height}, #{durationMs}, #{promptSnapshot}, #{generationConfig}, "
            + "#{assetMetadata}, #{assetStatus})")
    int insertIgnore(GeneratedAsset asset);
}

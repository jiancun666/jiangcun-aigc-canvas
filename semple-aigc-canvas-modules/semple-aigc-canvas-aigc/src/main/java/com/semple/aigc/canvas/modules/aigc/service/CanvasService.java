package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasEdge;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 画布聚合服务接口。
 */
public interface CanvasService extends IService<Canvas> {
    /**
     * 获取当前用户可读的完整画布文档。
     */
    CanvasDocument get(Long canvasId, Long userId);

    /**
     * 校验版本号并保存画布快照；节点按 nodeKey 差量更新以保持 ID 稳定。
     */
    CanvasDocument save(Long canvasId, SaveCanvasCommand command, Long userId);

    /**
     * 查询项目中的有效画布。
     */
    List<Canvas> listByProject(Long projectId, Long userId);

    /**
     * 为项目创建画布。
     */
    Canvas create(Long projectId, String name, Long userId);

    /**
     * 删除非默认画布。
     */
    void delete(Long canvasId, Long userId);

    /**
     * 查询画布版本摘要。
     */
    List<VersionInfo> versions(Long canvasId, Long userId);

    /**
     * 读取指定版本的画布内容。
     */
    SaveCanvasCommand version(Long canvasId, Integer revision, Long userId);

    /**
     * 将历史内容保存为新版本，并校验当前版本号。
     */
    CanvasDocument restore(Long canvasId, Integer revision, Integer expectedRevision, Long userId);

    /**
     * 在画布行锁内记录当前内容快照，供生成结果回写调用。
     */
    void recordVersion(Long canvasId, Long userId);

    /**
     * 画布版本摘要。
     */
    record VersionInfo(Integer revision, java.util.Date createdAt, Long createdByUserId) {
    }

    /**
     * 画布及其节点、连线的聚合返回对象。
     */
    record CanvasDocument(Canvas canvas, List<CanvasNode> nodes, List<CanvasEdge> edges) {
    }

    /**
     * 保存画布命令；空集合统一规范化，避免实现层重复判空。
     */
    @Schema(description = "画布全量快照；须携带当前版本及完整的节点、连线列表")
    record SaveCanvasCommand(
            @Schema(description = "画布名称，非空且最多 40 个字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试画布")
            String name,
            @Schema(description = "当前画布版本号，须等于 GET /canvases/{id} 返回的 canvas.lockVersion", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
            Integer revision,
            @Schema(description = "可选视口 JSON，用于保存画布平移、缩放等前端状态", type = "object", example = "{\"x\":0,\"y\":0,\"zoom\":1}")
            JsonNode viewport,
            @Schema(description = "可选画布设置 JSON，结构由前端定义", type = "object", example = "{}")
            JsonNode settings,
            @Schema(description = "画布的完整节点列表；省略、null 或空数组表示移除已有节点")
            List<NodeCommand> nodes,
            @Schema(description = "画布的完整连线列表；省略、null 或空数组表示移除已有连线")
            List<EdgeCommand> edges) {
        public SaveCanvasCommand {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    /**
     * 节点编辑态数据。
     */
    @Schema(description = "画布节点编辑参数，按 nodeKey 保存并保持节点数据库 ID 稳定")
    record NodeCommand(
            @Schema(description = "画布内唯一且稳定的节点键，非空，最多 64 个字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen-test")
            String nodeKey,
            @Schema(description = "节点类型：1 文本生成，2 图片生成，3 视频生成，4 音频生成，5 上传文件，6 历史资产，7 普通处理",
                    requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"1", "2", "3", "4", "5", "6", "7"}, example = "1")
            Integer nodeType,
            @Schema(description = "节点名称，非空，最多 100 个字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "文本测试")
            String name,
            @Schema(description = "选中的模型定义 ID；模型生成节点执行前须配置，并与 nodeType 对应；其他节点可为空", example = "1000")
            Long modelDefinitionId,
            @Schema(description = "节点在画布中的横坐标", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
            BigDecimal x,
            @Schema(description = "节点在画布中的纵坐标", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
            BigDecimal y,
            @Schema(description = "可选节点宽度", example = "320")
            BigDecimal width,
            @Schema(description = "可选节点高度", example = "200")
            BigDecimal height,
            @Schema(description = "节点输入 JSON；工作流从 prompt 字段读取提示词，为空时读取 data.prompt", type = "object",
                    example = "{\"prompt\":\"请用一句中文介绍你自己。\"}")
            JsonNode inputConfig,
            @Schema(description = "模型调用参数 JSON，具体字段由适配器决定；imageCount 表示期望输出数量", type = "object",
                    example = "{\"imageCount\":1,\"max_tokens\":128}")
            JsonNode modelConfig,
            @Schema(description = "节点扩展 JSON；引用素材时 assetId、generatedAssetId 须属于当前工作区；生成完成后写入 generation 结果", type = "object", example = "{}")
            JsonNode data) {
    }

    /**
     * 连线编辑态数据。
     */
    @Schema(description = "画布连线编辑参数，源节点和目标节点均通过 nodeKey 引用")
    record EdgeCommand(
            @Schema(description = "画布内唯一的连线键，非空，最多 64 个字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "edge-001")
            String edgeKey,
            @Schema(description = "源节点的 nodeKey，须出现在本次 nodes 列表中", requiredMode = Schema.RequiredMode.REQUIRED, example = "source-node")
            String sourceNodeKey,
            @Schema(description = "可选的源节点连接端口标识", example = "output")
            String sourceHandle,
            @Schema(description = "目标节点的 nodeKey，须出现在本次 nodes 列表中，且不得与源节点相同", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen-test")
            String targetNodeKey,
            @Schema(description = "可选的目标节点连接端口标识", example = "input")
            String targetHandle,
            @Schema(description = "可选连线扩展 JSON，结构由前端定义", type = "object", example = "{}")
            JsonNode data) {
    }
}

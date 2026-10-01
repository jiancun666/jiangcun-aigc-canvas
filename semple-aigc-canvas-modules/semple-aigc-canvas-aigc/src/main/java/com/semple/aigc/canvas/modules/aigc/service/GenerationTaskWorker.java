package com.semple.aigc.canvas.modules.aigc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期扫描数据库任务；数据库是任务事实源，服务重启后可继续执行。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationTaskWorker {
    private final GenerationTaskService generationTaskService;

    /** 执行等待任务并轮询异步供应商状态。 */
    @Scheduled(fixedDelayString = "${semple-aigc-canvas.generation.worker.fixed-delay-ms:2000}")
    public void dispatch() {
        try {
            generationTaskService.runDueTasks();
        } catch (Exception e) {
            log.error("Generation task worker batch failed", e);
        }
    }
}

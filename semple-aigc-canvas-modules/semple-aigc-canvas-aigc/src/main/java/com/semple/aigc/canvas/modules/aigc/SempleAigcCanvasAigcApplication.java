package com.semple.aigc.canvas.modules.aigc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Service entry point. */
@SpringBootApplication(scanBasePackages = "com.semple.aigc.canvas")
@MapperScan(basePackages = {"com.semple.aigc.canvas.api.aigc.mapper", "com.semple.aigc.canvas.modules.aigc.mapper"},
        annotationClass = Mapper.class)
@EnableScheduling
public class SempleAigcCanvasAigcApplication {
    public static void main(String[] args) {
        SpringApplication.run(SempleAigcCanvasAigcApplication.class, args);
    }
}

package com.semple.aigc.canvas.modules.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.semple.aigc.canvas", exclude = {DataSourceAutoConfiguration.class})
public class SempleAigcCanvasJobApplication {
    public static void main(String[] args) {
        SpringApplication.run(SempleAigcCanvasJobApplication.class, args);
    }
}

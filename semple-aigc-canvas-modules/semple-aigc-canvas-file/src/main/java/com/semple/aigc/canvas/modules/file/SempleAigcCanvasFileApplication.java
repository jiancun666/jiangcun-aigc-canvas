package com.semple.aigc.canvas.modules.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Service entry point.
 */
@SpringBootApplication(scanBasePackages = "com.semple.aigc.canvas", exclude = {DataSourceAutoConfiguration.class})
public class SempleAigcCanvasFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(SempleAigcCanvasFileApplication.class, args);
    }
}

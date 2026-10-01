package com.semple.aigc.canvas.modules.system;

 import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Service entry point. */
@SpringBootApplication(scanBasePackages = "com.semple.aigc.canvas")
public class SempleAigcCanvasSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(SempleAigcCanvasSystemApplication.class, args);
    }
}

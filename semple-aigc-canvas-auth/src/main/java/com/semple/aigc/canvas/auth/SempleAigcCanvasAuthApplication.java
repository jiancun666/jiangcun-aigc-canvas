package com.semple.aigc.canvas.auth;

 import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Service entry point. */
@SpringBootApplication(scanBasePackages = "com.semple.aigc.canvas")
public class SempleAigcCanvasAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(SempleAigcCanvasAuthApplication.class, args);
    }
}

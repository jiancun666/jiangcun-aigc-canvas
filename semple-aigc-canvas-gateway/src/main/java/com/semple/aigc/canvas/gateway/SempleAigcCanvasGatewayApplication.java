package com.semple.aigc.canvas.gateway;

 import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Service entry point. */
@SpringBootApplication(scanBasePackages = "com.semple.aigc.canvas")
public class SempleAigcCanvasGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(SempleAigcCanvasGatewayApplication.class, args);
    }
}

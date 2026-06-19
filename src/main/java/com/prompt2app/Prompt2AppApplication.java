package com.prompt2app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
@MapperScan(basePackages = {"com.prompt2app.app.mapper", "com.prompt2app.metric.mapper"})
public class Prompt2AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(Prompt2AppApplication.class, args);
    }

}

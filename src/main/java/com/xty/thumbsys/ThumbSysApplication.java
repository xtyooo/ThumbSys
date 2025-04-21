package com.xty.thumbsys;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.xty.thumbsys.mapper")
@EnableScheduling
public class ThumbSysApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThumbSysApplication.class, args);
    }

}

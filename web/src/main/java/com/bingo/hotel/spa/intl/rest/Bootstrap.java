package com.bingo.hotel.spa.intl.rest;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.bingo.hotel"})
@EnableFeignClients(basePackages = "com.bingo.hotel")
@MapperScan(basePackages = {"com.bingo.hotel.spa.intl.core.api.common.mapper",
        "com.bingo.hotel.spa.intl.core.api.ratehawk.mapper"})
@ServletComponentScan
@EnableAsync
public class Bootstrap {

    public static void main(String[] args) {
        SpringApplication.run(Bootstrap.class, args);
    }

}

package com.trip.booking.spa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = {
        "com.trip.booking.spa.gateway.adapter.outbound.state.dao.mapper",
        "com.trip.booking.spa.gateway.adapter.outbound.state.catalog"
})
@ServletComponentScan
@EnableAsync
@EnableScheduling
public class TripBookingSpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripBookingSpaApplication.class, args);
    }
}

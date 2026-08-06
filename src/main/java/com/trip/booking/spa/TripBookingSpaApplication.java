package com.trip.booking.spa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan(basePackages = {
        "com.trip.booking.spa.core.api.common.mapper",
        "com.trip.booking.spa.core.api.ratehawk.mapper",
        "com.trip.booking.spa.core.api.expedia.mapper"
})
@ServletComponentScan
@EnableAsync
public class TripBookingSpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripBookingSpaApplication.class, args);
    }
}

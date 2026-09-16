package com.akshaya.outlast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan

public class OutlastApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutlastApplication.class, args);
    }

}

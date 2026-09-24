package com.akshaya.elmsbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class CompanyTimeConfig {

    @Bean
    Clock companyClock(@Value("${app.leave.time-zone:Asia/Kolkata}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}

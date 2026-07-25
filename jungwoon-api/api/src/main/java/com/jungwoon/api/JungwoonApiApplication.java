package com.jungwoon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.jungwoon")
@EnableJpaAuditing
@EntityScan(basePackages = "com.jungwoon.domain")
@EnableJpaRepositories(basePackages = "com.jungwoon.domain")
public class JungwoonApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JungwoonApiApplication.class, args);
    }
}

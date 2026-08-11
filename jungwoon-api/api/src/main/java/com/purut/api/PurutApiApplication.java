package com.purut.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.purut")
@EnableJpaAuditing
@EntityScan(basePackages = "com.purut.domain")
@EnableJpaRepositories(basePackages = "com.purut.domain")
public class PurutApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurutApiApplication.class, args);
    }
}

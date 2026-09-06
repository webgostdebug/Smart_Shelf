package com.smartshelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartShelfApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartShelfApplication.class, args);
    }
}
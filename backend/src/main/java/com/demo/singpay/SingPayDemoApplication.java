package com.demo.singpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SingPayDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SingPayDemoApplication.class, args);
    }
}

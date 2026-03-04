package com.exam.txtemplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class TransactionTemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionTemplateApplication.class, args);
    }
}

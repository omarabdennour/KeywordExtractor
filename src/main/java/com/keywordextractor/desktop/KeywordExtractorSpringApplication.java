package com.keywordextractor.desktop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public class KeywordExtractorSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(KeywordExtractorSpringApplication.class, args);
    }
}

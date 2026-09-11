package com.corebank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.corebank.domain")
@ComponentScan(basePackages = {"com.corebank.gateway", "com.corebank.domain"})
@EnableJpaRepositories(basePackages = "com.corebank.domain.repository")
public class CorebankGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(CorebankGatewayApplication.class, args);
  }
}

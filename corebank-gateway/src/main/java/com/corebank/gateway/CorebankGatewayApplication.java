package com.corebank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = "com.corebank.domain")
public class CorebankGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(CorebankGatewayApplication.class, args);
  }
}

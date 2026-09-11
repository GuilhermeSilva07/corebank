package com.corebank.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.corebank.domain")
@ComponentScan(basePackages = {"com.corebank.scheduler", "com.corebank.domain"})
@EnableJpaRepositories(basePackages = "com.corebank.domain.repository")
public class CorebankSchedulerApplication {

  public static void main(String[] args) {
    SpringApplication.run(CorebankSchedulerApplication.class, args);
  }
}

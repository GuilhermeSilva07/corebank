package com.corebank.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CorebankSchedulerApplication {

  public static void main(String[] args) {
    SpringApplication.run(CorebankSchedulerApplication.class, args);
  }
}

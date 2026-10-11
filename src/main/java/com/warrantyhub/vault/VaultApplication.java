package com.warrantyhub.vault;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VaultApplication {
  public static void main(String[] args) {
    SpringApplication.run(VaultApplication.class, args);
  }

  @Bean
  org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator(Clock clock) {
    var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
    validator.setConfigurationInitializer(config -> config.clockProvider(() -> clock));
    return validator;
  }

  @Bean
  Clock clock() {
    return Clock.system(java.time.ZoneId.of("Asia/Kolkata"));
  }
}

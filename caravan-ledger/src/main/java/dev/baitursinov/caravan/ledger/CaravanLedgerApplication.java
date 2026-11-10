package dev.baitursinov.caravan.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class CaravanLedgerApplication {

  static void main(String[] args) {
    SpringApplication.run(CaravanLedgerApplication.class, args);
  }
}

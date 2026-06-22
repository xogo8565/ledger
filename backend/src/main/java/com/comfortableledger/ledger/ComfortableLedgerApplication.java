package com.comfortableledger.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ComfortableLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComfortableLedgerApplication.class, args);
    }
}

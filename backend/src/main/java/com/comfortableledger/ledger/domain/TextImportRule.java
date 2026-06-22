package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "text_import_rules")
public class TextImportRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String issuerName;
    private String amountPattern;
    private String merchantPattern;
    private String datePattern;

    protected TextImportRule() {
    }
}

package com.comfortableledger.ledger.service;

import org.springframework.data.domain.Sort;

public enum TransactionSearchSort {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC;

    public Sort toSpringSort() {
        return switch (this) {
            case DATE_ASC -> Sort.by(
                    Sort.Order.asc("transactionDate"),
                    Sort.Order.asc("id")
            );
            case AMOUNT_DESC -> Sort.by(
                    Sort.Order.desc("amount"),
                    Sort.Order.desc("transactionDate"),
                    Sort.Order.desc("id")
            );
            case AMOUNT_ASC -> Sort.by(
                    Sort.Order.asc("amount"),
                    Sort.Order.desc("transactionDate"),
                    Sort.Order.desc("id")
            );
            case DATE_DESC -> Sort.by(
                    Sort.Order.desc("transactionDate"),
                    Sort.Order.desc("id")
            );
        };
    }
}

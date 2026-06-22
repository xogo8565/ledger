package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "card_profiles")
public class CardProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    @NotNull
    private Asset asset;

    @OneToOne(fetch = FetchType.LAZY)
    private Asset paymentAccount;

    @Min(1)
    @Max(31)
    private int statementClosingDay;

    @Min(1)
    @Max(31)
    private int paymentDay;

    private boolean autoPayment;

    protected CardProfile() {
    }

    public CardProfile(Asset asset, Asset paymentAccount, int statementClosingDay, int paymentDay, boolean autoPayment) {
        this.asset = asset;
        this.paymentAccount = paymentAccount;
        this.statementClosingDay = statementClosingDay;
        this.paymentDay = paymentDay;
        this.autoPayment = autoPayment;
    }

    public Long getId() {
        return id;
    }

    public Asset getPaymentAccount() {
        return paymentAccount;
    }

    public int getStatementClosingDay() {
        return statementClosingDay;
    }

    public int getPaymentDay() {
        return paymentDay;
    }

    public boolean isAutoPayment() {
        return autoPayment;
    }
}

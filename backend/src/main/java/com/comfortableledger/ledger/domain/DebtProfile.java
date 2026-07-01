package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "debt_profiles")
public class DebtProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    @NotNull
    private Asset asset;

    @OneToOne(fetch = FetchType.LAZY)
    private Asset paymentAccount;

    @DecimalMin("0.0")
    @NotNull
    private BigDecimal annualInterestRate;

    @Min(1)
    @Max(31)
    private int paymentDay;

    private boolean autoDeduct;
    private String lastDeductedMonth;

    protected DebtProfile() {
    }

    public DebtProfile(Asset asset, Asset paymentAccount, BigDecimal annualInterestRate,
                       int paymentDay, boolean autoDeduct) {
        this.asset = asset;
        this.paymentAccount = paymentAccount;
        this.annualInterestRate = annualInterestRate;
        this.paymentDay = paymentDay;
        this.autoDeduct = autoDeduct;
    }

    public Asset getAsset() {
        return asset;
    }

    public Long getId() {
        return id;
    }

    public Asset getPaymentAccount() {
        return paymentAccount;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public int getPaymentDay() {
        return paymentDay;
    }

    public boolean isAutoDeduct() {
        return autoDeduct;
    }

    public String getLastDeductedMonth() {
        return lastDeductedMonth;
    }

    public void update(Asset paymentAccount, BigDecimal annualInterestRate, int paymentDay, boolean autoDeduct) {
        this.paymentAccount = paymentAccount;
        this.annualInterestRate = annualInterestRate == null ? BigDecimal.ZERO : annualInterestRate;
        this.paymentDay = paymentDay;
        this.autoDeduct = autoDeduct;
    }

    public void markDeducted(String month) {
        this.lastDeductedMonth = month;
    }
}

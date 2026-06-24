package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
public class TransactionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member author;

    @Enumerated(EnumType.STRING)
    @NotNull
    private TransactionType type;

    @NotNull
    private LocalDate transactionDate;

    @Positive
    @NotNull
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    private Asset fromAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    private Asset toAsset;

    private String title;
    private String memo;
    private String spendingTag;
    private int installmentMonths;
    private int installmentIndex;
    private String installmentGroupId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected TransactionRecord() {
    }

    public TransactionRecord(Household household, Member author, TransactionType type, LocalDate transactionDate,
                             BigDecimal amount, Category category, Asset asset, Asset fromAsset, Asset toAsset,
                             String title, String memo, String spendingTag, int installmentMonths) {
        this.household = household;
        this.author = author;
        this.type = type;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.category = category;
        this.asset = asset;
        this.fromAsset = fromAsset;
        this.toAsset = toAsset;
        this.title = title;
        this.memo = memo;
        this.spendingTag = spendingTag;
        this.installmentMonths = installmentMonths;
        this.installmentIndex = installmentMonths > 1 ? 1 : 0;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Category getCategory() {
        return category;
    }

    public Asset getAsset() {
        return asset;
    }

    public Asset getFromAsset() {
        return fromAsset;
    }

    public Asset getToAsset() {
        return toAsset;
    }

    public String getTitle() {
        return title;
    }

    public String getMemo() {
        return memo;
    }

    public String getSpendingTag() {
        return spendingTag;
    }

    public int getInstallmentMonths() {
        return installmentMonths;
    }

    public int getInstallmentIndex() {
        return installmentIndex;
    }

    public String getInstallmentGroupId() {
        return installmentGroupId;
    }

    public void assignInstallment(String installmentGroupId, int installmentIndex, int installmentMonths) {
        this.installmentGroupId = installmentGroupId;
        this.installmentIndex = installmentIndex;
        this.installmentMonths = installmentMonths;
    }

    public void update(TransactionType type, LocalDate transactionDate, BigDecimal amount, 
                       Category category, Asset asset, Asset fromAsset, Asset toAsset,
                       String title, String memo, String spendingTag, int installmentMonths) {
        this.type = type;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.category = category;
        this.asset = asset;
        this.fromAsset = fromAsset;
        this.toAsset = toAsset;
        this.title = title;
        this.memo = memo;
        this.spendingTag = spendingTag;
        this.installmentMonths = installmentMonths;
        this.installmentIndex = installmentMonths > 1 && this.installmentIndex == 0 ? 1 : this.installmentIndex;
        this.updatedAt = OffsetDateTime.now();
    }
}

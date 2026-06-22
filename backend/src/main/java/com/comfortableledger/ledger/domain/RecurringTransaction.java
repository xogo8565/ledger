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
@Table(name = "recurring_transactions")
public class RecurringTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @Enumerated(EnumType.STRING)
    @NotNull
    private TransactionType type;

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
    private int installmentMonths;

    @Enumerated(EnumType.STRING)
    @NotNull
    private RecurrenceFrequency frequency;

    private int intervalValue;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private LocalDate nextRunDate;

    private boolean active = true;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected RecurringTransaction() {
    }

    public RecurringTransaction(Household household, TransactionType type, BigDecimal amount, Category category,
                                Asset asset, Asset fromAsset, Asset toAsset, String title, String memo,
                                int installmentMonths, RecurrenceFrequency frequency, int intervalValue,
                                LocalDate startDate, LocalDate endDate) {
        this.household = household;
        update(type, amount, category, asset, fromAsset, toAsset, title, memo, installmentMonths,
                frequency, intervalValue, startDate, endDate, startDate);
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
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

    public int getInstallmentMonths() {
        return installmentMonths;
    }

    public RecurrenceFrequency getFrequency() {
        return frequency;
    }

    public int getIntervalValue() {
        return intervalValue;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public boolean isActive() {
        return active;
    }

    public void update(TransactionType type, BigDecimal amount, Category category, Asset asset, Asset fromAsset,
                       Asset toAsset, String title, String memo, int installmentMonths,
                       RecurrenceFrequency frequency, int intervalValue, LocalDate startDate,
                       LocalDate endDate, LocalDate nextRunDate) {
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.asset = asset;
        this.fromAsset = fromAsset;
        this.toAsset = toAsset;
        this.title = title;
        this.memo = memo;
        this.installmentMonths = installmentMonths;
        this.frequency = frequency;
        this.intervalValue = intervalValue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextRunDate = nextRunDate;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markGenerated(LocalDate nextRunDate) {
        this.nextRunDate = nextRunDate;
        this.updatedAt = OffsetDateTime.now();
        if (endDate != null && nextRunDate.isAfter(endDate)) {
            this.active = false;
        }
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = OffsetDateTime.now();
    }
}

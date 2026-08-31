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

    // 반복 패턴(예: "매달 31일")의 기준일. 사용자가 등록/수정할 때 지정한 다음 실행일로
    // 고정되며, 자동 생성(markGenerated) 시에는 변하지 않는다. 매달/매년 다음 실행일을
    // 계산할 때 직전 실행일이 아닌 이 값을 기준으로 다시 계산해, 짧은 달(2월 등)을
    // 지나며 날짜가 클램프되더라도 이후 큰 달에서 원래 날짜로 복귀할 수 있게 한다.
    private LocalDate anchorDate;

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

    public LocalDate getAnchorDate() {
        // 이 컬럼이 생기기 전에 만들어진 기존 반복 거래는 anchorDate가 비어 있으므로
        // 시작일을 기준일로 대신 사용한다.
        return anchorDate == null ? startDate : anchorDate;
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
        this.anchorDate = nextRunDate;
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

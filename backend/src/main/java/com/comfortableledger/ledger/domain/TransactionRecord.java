package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_tx_household_date_id", columnList = "household_id, transaction_date, id"),
                @Index(name = "idx_tx_household_type_date", columnList = "household_id, type, transaction_date"),
                @Index(name = "idx_tx_household_category_date", columnList = "household_id, category_id, transaction_date"),
                @Index(name = "idx_tx_household_consumer_date", columnList = "household_id, consumer_id, transaction_date"),
                @Index(name = "idx_tx_household_scope_date", columnList = "household_id, consumption_scope, transaction_date"),
                @Index(name = "idx_tx_household_amount_date", columnList = "household_id, amount, transaction_date, id"),
                @Index(name = "idx_tx_household_asset_date", columnList = "household_id, asset_id, transaction_date"),
                @Index(name = "idx_tx_household_from_asset_date", columnList = "household_id, from_asset_id, transaction_date"),
                @Index(name = "idx_tx_household_to_asset_date", columnList = "household_id, to_asset_id, transaction_date"),
                @Index(name = "idx_tx_installment_group", columnList = "installment_group_id")
        }
)
public class TransactionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member consumer;

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

    @Enumerated(EnumType.STRING)
    private ConsumptionScope consumptionScope;

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
        this(household, author, type, transactionDate, amount, category, asset, fromAsset, toAsset,
                title, memo, spendingTag, null, null, installmentMonths);
    }

    public TransactionRecord(Household household, Member author, TransactionType type, LocalDate transactionDate,
                             BigDecimal amount, Category category, Asset asset, Asset fromAsset, Asset toAsset,
                             String title, String memo, String spendingTag, ConsumptionScope consumptionScope,
                             int installmentMonths) {
        this(household, author, type, transactionDate, amount, category, asset, fromAsset, toAsset,
                title, memo, spendingTag, consumptionScope, null, installmentMonths);
    }

    public TransactionRecord(Household household, Member author, TransactionType type, LocalDate transactionDate,
                             BigDecimal amount, Category category, Asset asset, Asset fromAsset, Asset toAsset,
                             String title, String memo, String spendingTag, ConsumptionScope consumptionScope,
                             Member consumer, int installmentMonths) {
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
        this.consumptionScope = normalizedConsumptionScope(type, consumptionScope);
        this.consumer = normalizedConsumer(type, this.consumptionScope, consumer);
        this.installmentMonths = installmentMonths;
        this.installmentIndex = installmentMonths > 1 ? 1 : 0;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
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

    public ConsumptionScope getConsumptionScope() {
        return normalizedConsumptionScope(type, consumptionScope);
    }

    public Member getConsumer() {
        return normalizedConsumer(type, getConsumptionScope(), consumer);
    }

    public boolean assignConsumerIfUnassignedPersonalExpense(Member member) {
        if (type != TransactionType.EXPENSE
                || getConsumptionScope() != ConsumptionScope.PERSONAL
                || consumer != null
                || member == null
                || (!Objects.equals(household.getId(), member.getHousehold().getId())
                    && household != member.getHousehold())) {
            return false;
        }
        consumer = member;
        updatedAt = OffsetDateTime.now();
        return true;
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
                       String title, String memo, String spendingTag, ConsumptionScope consumptionScope,
                       int installmentMonths) {
        update(type, transactionDate, amount, category, asset, fromAsset, toAsset, title, memo, spendingTag,
                consumptionScope, null, installmentMonths);
    }

    public void update(TransactionType type, LocalDate transactionDate, BigDecimal amount,
                       Category category, Asset asset, Asset fromAsset, Asset toAsset,
                       String title, String memo, String spendingTag, ConsumptionScope consumptionScope,
                       Member consumer, int installmentMonths) {
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
        this.consumptionScope = normalizedConsumptionScope(type, consumptionScope);
        this.consumer = normalizedConsumer(type, this.consumptionScope, consumer);
        this.installmentMonths = installmentMonths;
        this.installmentIndex = installmentMonths > 1 && this.installmentIndex == 0 ? 1 : this.installmentIndex;
        this.updatedAt = OffsetDateTime.now();
    }

    private ConsumptionScope normalizedConsumptionScope(TransactionType transactionType, ConsumptionScope scope) {
        if (transactionType != TransactionType.EXPENSE) {
            return null;
        }
        return scope == null ? ConsumptionScope.PERSONAL : scope;
    }

    private Member normalizedConsumer(TransactionType transactionType, ConsumptionScope scope, Member member) {
        return transactionType == TransactionType.EXPENSE && scope == ConsumptionScope.PERSONAL ? member : null;
    }
}

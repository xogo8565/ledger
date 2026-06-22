package com.comfortableledger.ledger.web;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.RecurrenceFrequency;
import com.comfortableledger.ledger.domain.RecurringTransaction;
import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record AssetDto(
            Long id,
            AssetType type,
            String name,
            BigDecimal balance,
            String groupName,
            String memo,
            CardDto card
    ) {
        public static AssetDto from(Asset asset) {
            CardDto card = asset.getCardProfile() == null
                    ? null
                    : new CardDto(
                    asset.getCardProfile().getPaymentAccount() == null ? null : asset.getCardProfile().getPaymentAccount().getId(),
                    asset.getCardProfile().getStatementClosingDay(),
                    asset.getCardProfile().getPaymentDay(),
                    asset.getCardProfile().isAutoPayment()
            );
            return new AssetDto(
                    asset.getId(),
                    asset.getType(),
                    asset.getName(),
                    asset.getBalance(),
                    asset.getGroupName(),
                    asset.getMemo(),
                    card
            );
        }
    }

    public record SaveAssetRequest(
            @NotNull AssetType type,
            @NotBlank String name,
            @NotNull BigDecimal balance,
            String groupName,
            String memo
    ) {
    }

    public record SaveCardAssetRequest(
            @NotBlank String name,
            @NotNull BigDecimal balance,
            String groupName,
            String memo,
            @NotNull Long paymentAccountId,
            int statementClosingDay,
            int paymentDay,
            boolean autoPayment
    ) {
    }

    public record CardDto(Long paymentAccountId, int statementClosingDay, int paymentDay, boolean autoPayment) {
    }

    public record CardDetailDto(Long id, String name, BigDecimal balance, Long paymentAccountId, 
                                int statementClosingDay, int paymentDay, boolean autoPayment,
                                BigDecimal unpaidAmount, BigDecimal paymentScheduleAmount) {
    }

    public record CategoryDto(Long id, CategoryType type, String name, String icon, String color) {
        public static CategoryDto from(Category category) {
            return new CategoryDto(category.getId(), category.getType(), category.getName(), category.getIcon(), category.getColor());
        }
    }

    public record SaveCategoryRequest(
            @NotNull CategoryType type,
            @NotBlank String name,
            String icon,
            String color
    ) {
    }

    public record TransactionDto(
            Long id,
            TransactionType type,
            LocalDate transactionDate,
            BigDecimal amount,
            Long categoryId,
            String categoryName,
            String categoryIcon,
            Long assetId,
            String assetName,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            int installmentMonths,
            int installmentIndex,
            String installmentGroupId
    ) {
        public static TransactionDto from(TransactionRecord record) {
            return new TransactionDto(
                    record.getId(),
                    record.getType(),
                    record.getTransactionDate(),
                    record.getAmount(),
                    record.getCategory() == null ? null : record.getCategory().getId(),
                    record.getCategory() == null ? null : record.getCategory().getName(),
                    record.getCategory() == null ? null : record.getCategory().getIcon(),
                    record.getAsset() == null ? null : record.getAsset().getId(),
                    record.getAsset() == null ? null : record.getAsset().getName(),
                    record.getFromAsset() == null ? null : record.getFromAsset().getId(),
                    record.getToAsset() == null ? null : record.getToAsset().getId(),
                    record.getTitle(),
                    record.getMemo(),
                    record.getInstallmentMonths(),
                    record.getInstallmentIndex(),
                    record.getInstallmentGroupId()
            );
        }
    }

    public record CreateTransactionRequest(
            @NotNull TransactionType type,
            @NotNull LocalDate transactionDate,
            @Positive @NotNull BigDecimal amount,
            Long categoryId,
            Long assetId,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            Integer installmentMonths
    ) {
    }

    public record MonthlySummaryDto(
            String month,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal transfer,
            BigDecimal assetTotal,
            BigDecimal liabilityTotal,
            BigDecimal netWorth,
            BigDecimal budget,
            BigDecimal remainingBudget,
            BigDecimal budgetUsageRate,
            List<CategorySpend> categorySpends
    ) {
        public record CategorySpend(String categoryName, BigDecimal amount) {
        }
    }

    public record CategoryBudgetDto(Long categoryId, String categoryName, String categoryIcon, BigDecimal amount) {
        public static CategoryBudgetDto empty(Category category) {
            return new CategoryBudgetDto(category.getId(), category.getName(), category.getIcon(), BigDecimal.ZERO);
        }
    }

    public record BudgetSettingsDto(String month, BigDecimal totalAmount, List<CategoryBudgetDto> categories) {
    }

    public record SaveBudgetRequest(String month, @NotNull BigDecimal totalAmount, List<SaveCategoryBudget> categories) {
        public record SaveCategoryBudget(@NotNull Long categoryId, @NotNull BigDecimal amount) {
        }
    }

    public record TextImportRequest(String rawText) {
    }

    public record TextImportPreview(
            String rawText,
            TransactionType type,
            LocalDate transactionDate,
            BigDecimal amount,
            String merchant,
            String memo
    ) {
    }

    public record ReceiptDto(Long id, String originalFilename, String storedPath) {
        public static ReceiptDto from(ReceiptAttachment attachment) {
            return new ReceiptDto(attachment.getId(), attachment.getOriginalFilename(), attachment.getStoredPath());
        }
    }

    public record BootstrapDto(
            List<AssetDto> assets,
            List<CategoryDto> categories,
            List<TransactionDto> transactions,
            MonthlySummaryDto summary
    ) {
    }

    public record AssetSummaryDto(BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal netWorth) {
    }

    public record CardPaymentScheduleDto(
            Long id,
            Long cardAssetId,
            LocalDate scheduledDate,
            BigDecimal amount,
            String status
    ) {
        public static CardPaymentScheduleDto from(com.comfortableledger.ledger.domain.CardPaymentSchedule schedule) {
            return new CardPaymentScheduleDto(
                    schedule.getId(),
                    schedule.getCardAsset().getId(),
                    schedule.getScheduledDate(),
                    schedule.getAmount(),
                    schedule.getStatus().toString()
            );
        }
    }

    public record CreatePaymentScheduleRequest(
            @NotNull LocalDate scheduledDate,
            @NotNull @Positive BigDecimal amount
    ) {
    }

    public record SchedulePaymentRequest(
            @NotNull Long scheduleId
    ) {
    }

    public record RecurringTransactionDto(
            Long id,
            TransactionType type,
            BigDecimal amount,
            Long categoryId,
            String categoryName,
            Long assetId,
            String assetName,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            int installmentMonths,
            RecurrenceFrequency frequency,
            int intervalValue,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextRunDate,
            boolean active
    ) {
        public static RecurringTransactionDto from(RecurringTransaction rule) {
            return new RecurringTransactionDto(
                    rule.getId(),
                    rule.getType(),
                    rule.getAmount(),
                    rule.getCategory() == null ? null : rule.getCategory().getId(),
                    rule.getCategory() == null ? null : rule.getCategory().getName(),
                    rule.getAsset() == null ? null : rule.getAsset().getId(),
                    rule.getAsset() == null ? null : rule.getAsset().getName(),
                    rule.getFromAsset() == null ? null : rule.getFromAsset().getId(),
                    rule.getToAsset() == null ? null : rule.getToAsset().getId(),
                    rule.getTitle(),
                    rule.getMemo(),
                    rule.getInstallmentMonths(),
                    rule.getFrequency(),
                    rule.getIntervalValue(),
                    rule.getStartDate(),
                    rule.getEndDate(),
                    rule.getNextRunDate(),
                    rule.isActive()
            );
        }
    }

    public record SaveRecurringTransactionRequest(
            @NotNull TransactionType type,
            @Positive @NotNull BigDecimal amount,
            Long categoryId,
            Long assetId,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            Integer installmentMonths,
            @NotNull RecurrenceFrequency frequency,
            @Positive Integer intervalValue,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            LocalDate nextRunDate
    ) {
    }

    public record RecurringGenerationResult(int generatedCount) {
    }
}

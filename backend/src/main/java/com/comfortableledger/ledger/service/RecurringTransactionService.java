package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.RecurringTransaction;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.RecurringTransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.web.ApiDtos.RecurringGenerationResult;
import com.comfortableledger.ledger.web.ApiDtos.RecurringTransactionDto;
import com.comfortableledger.ledger.web.ApiDtos.SaveRecurringTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringTransactionService {
    private final HouseholdRepository householdRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final LedgerService ledgerService;

    public RecurringTransactionService(HouseholdRepository householdRepository, AssetRepository assetRepository,
                                       CategoryRepository categoryRepository,
                                       RecurringTransactionRepository recurringTransactionRepository,
                                       LedgerService ledgerService) {
        this.householdRepository = householdRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionDto> recurringTransactions() {
        return recurringTransactionRepository.findByHouseholdIdAndActiveTrueOrderByNextRunDateAscIdAsc(defaultHousehold().getId())
                .stream()
                .map(RecurringTransactionDto::from)
                .toList();
    }

    @Transactional
    public RecurringTransactionDto createRecurringTransaction(SaveRecurringTransactionRequest request) {
        validateRequest(request);
        Household household = defaultHousehold();
        RecurringTransaction recurringTransaction = new RecurringTransaction(
                household,
                request.type(),
                request.amount(),
                category(request.categoryId()),
                asset(request.assetId()),
                asset(request.fromAssetId()),
                asset(request.toAssetId()),
                request.title(),
                request.memo(),
                request.installmentMonths() == null ? 0 : request.installmentMonths(),
                request.frequency(),
                intervalValue(request.intervalValue()),
                request.startDate(),
                request.endDate()
        );
        LocalDate nextRunDate = request.nextRunDate() == null ? request.startDate() : request.nextRunDate();
        recurringTransaction.update(
                recurringTransaction.getType(),
                recurringTransaction.getAmount(),
                recurringTransaction.getCategory(),
                recurringTransaction.getAsset(),
                recurringTransaction.getFromAsset(),
                recurringTransaction.getToAsset(),
                recurringTransaction.getTitle(),
                recurringTransaction.getMemo(),
                recurringTransaction.getInstallmentMonths(),
                recurringTransaction.getFrequency(),
                recurringTransaction.getIntervalValue(),
                recurringTransaction.getStartDate(),
                recurringTransaction.getEndDate(),
                nextRunDate
        );
        return RecurringTransactionDto.from(recurringTransactionRepository.save(recurringTransaction));
    }

    @Transactional
    public RecurringTransactionDto updateRecurringTransaction(Long id, SaveRecurringTransactionRequest request) {
        validateRequest(request);
        RecurringTransaction recurringTransaction = recurringTransactionRepository.findById(id).orElseThrow();
        recurringTransaction.update(
                request.type(),
                request.amount(),
                category(request.categoryId()),
                asset(request.assetId()),
                asset(request.fromAssetId()),
                asset(request.toAssetId()),
                request.title(),
                request.memo(),
                request.installmentMonths() == null ? 0 : request.installmentMonths(),
                request.frequency(),
                intervalValue(request.intervalValue()),
                request.startDate(),
                request.endDate(),
                request.nextRunDate() == null ? request.startDate() : request.nextRunDate()
        );
        return RecurringTransactionDto.from(recurringTransaction);
    }

    @Transactional
    public void deleteRecurringTransaction(Long id) {
        recurringTransactionRepository.findById(id).orElseThrow().deactivate();
    }

    @Transactional
    public RecurringGenerationResult generateDueTransactions(LocalDate upToDate) {
        LocalDate targetDate = upToDate == null ? LocalDate.now() : upToDate;
        List<RecurringTransaction> rules = recurringTransactionRepository
                .findByHouseholdIdAndActiveTrueAndNextRunDateLessThanEqualOrderByNextRunDateAscIdAsc(
                        defaultHousehold().getId(),
                        targetDate
                );
        int generatedCount = 0;
        for (RecurringTransaction rule : rules) {
            while (rule.isActive() && !rule.getNextRunDate().isAfter(targetDate)) {
                if (rule.getEndDate() != null && rule.getNextRunDate().isAfter(rule.getEndDate())) {
                    rule.deactivate();
                    break;
                }
                ledgerService.createTransaction(new CreateTransactionRequest(
                        rule.getType(),
                        rule.getNextRunDate(),
                        rule.getAmount(),
                        rule.getCategory() == null ? null : rule.getCategory().getId(),
                        rule.getAsset() == null ? null : rule.getAsset().getId(),
                        rule.getFromAsset() == null ? null : rule.getFromAsset().getId(),
                        rule.getToAsset() == null ? null : rule.getToAsset().getId(),
                        rule.getTitle(),
                        rule.getMemo(),
                        null,
                        null,
                        null,
                        rule.getInstallmentMonths()
                ));
                generatedCount++;
                rule.markGenerated(nextRunDate(rule));
            }
        }
        return new RecurringGenerationResult(generatedCount);
    }

    private LocalDate nextRunDate(RecurringTransaction rule) {
        int interval = intervalValue(rule.getIntervalValue());
        return switch (rule.getFrequency()) {
            case DAILY -> rule.getNextRunDate().plusDays(interval);
            case WEEKLY -> rule.getNextRunDate().plusWeeks(interval);
            case MONTHLY -> rule.getNextRunDate().plusMonths(interval);
            case YEARLY -> rule.getNextRunDate().plusYears(interval);
        };
    }

    private int intervalValue(Integer intervalValue) {
        return intervalValue == null || intervalValue < 1 ? 1 : intervalValue;
    }

    private void validateRequest(SaveRecurringTransactionRequest request) {
        if (request.type() == null) {
            throw new IllegalArgumentException("Recurring transaction type is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Recurring transaction amount must be positive");
        }
        if (request.frequency() == null) {
            throw new IllegalArgumentException("Recurring frequency is required");
        }
        if (request.startDate() == null) {
            throw new IllegalArgumentException("Recurring start date is required");
        }
        LocalDate nextRunDate = request.nextRunDate() == null ? request.startDate() : request.nextRunDate();
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Recurring end date cannot be before start date");
        }
        if (request.endDate() != null && nextRunDate.isAfter(request.endDate())) {
            throw new IllegalArgumentException("Recurring next run date cannot be after end date");
        }
    }

    private Category category(Long id) {
        return id == null ? null : categoryRepository.findById(id).orElseThrow();
    }

    private Asset asset(Long id) {
        return id == null ? null : assetRepository.findById(id).orElseThrow();
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}

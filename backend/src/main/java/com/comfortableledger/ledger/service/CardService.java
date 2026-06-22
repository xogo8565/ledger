package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.CardPaymentSchedule;
import com.comfortableledger.ledger.domain.PaymentStatus;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CardProfileRepository;
import com.comfortableledger.ledger.repo.CardPaymentScheduleRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.CardDetailDto;
import com.comfortableledger.ledger.web.ApiDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.web.ApiDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.web.ApiDtos.TransactionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {
    private final AssetRepository assetRepository;
    private final CardProfileRepository cardProfileRepository;
    private final TransactionRepository transactionRepository;
    private final CardPaymentScheduleRepository cardPaymentScheduleRepository;

    public CardService(AssetRepository assetRepository, CardProfileRepository cardProfileRepository,
                       TransactionRepository transactionRepository, CardPaymentScheduleRepository cardPaymentScheduleRepository) {
        this.assetRepository = assetRepository;
        this.cardProfileRepository = cardProfileRepository;
        this.transactionRepository = transactionRepository;
        this.cardPaymentScheduleRepository = cardPaymentScheduleRepository;
    }

    @Transactional(readOnly = true)
    public CardDetailDto cardDetail(Long cardAssetId) {
        Asset cardAsset = assetRepository.findById(cardAssetId).orElseThrow();
        if (cardAsset.getType() != AssetType.CARD) {
            throw new IllegalArgumentException("Card asset type expected");
        }

        CardProfile cardProfile = cardAsset.getCardProfile();
        if (cardProfile == null) {
            throw new IllegalArgumentException("Card profile not found");
        }

        BigDecimal unpaidAmount = calculateUnpaidAmount(cardAsset);
        BigDecimal paymentScheduleAmount = calculatePaymentScheduleAmount(cardAsset);

        return new CardDetailDto(
                cardAsset.getId(),
                cardAsset.getName(),
                cardAsset.getBalance(),
                cardProfile.getPaymentAccount().getId(),
                cardProfile.getStatementClosingDay(),
                cardProfile.getPaymentDay(),
                cardProfile.isAutoPayment(),
                unpaidAmount,
                paymentScheduleAmount
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal getUnpaidAmount(Long cardAssetId) {
        Asset cardAsset = assetRepository.findById(cardAssetId).orElseThrow();
        if (cardAsset.getType() != AssetType.CARD) {
            throw new IllegalArgumentException("Card asset type expected");
        }
        return calculateUnpaidAmount(cardAsset);
    }

    @Transactional(readOnly = true)
    public BigDecimal getPaymentScheduleAmount(Long cardAssetId) {
        Asset cardAsset = assetRepository.findById(cardAssetId).orElseThrow();
        if (cardAsset.getType() != AssetType.CARD) {
            throw new IllegalArgumentException("Card asset type expected");
        }
        return calculatePaymentScheduleAmount(cardAsset);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getBillingPeriodTransactions(Long cardAssetId) {
        Asset cardAsset = assetRepository.findById(cardAssetId).orElseThrow();
        if (cardAsset.getType() != AssetType.CARD) {
            throw new IllegalArgumentException("Card asset type expected");
        }

        CardProfile cardProfile = cardAsset.getCardProfile();
        if (cardProfile == null) {
            throw new IllegalArgumentException("Card profile not found");
        }

        LocalDate billingStart = calculateBillingPeriodStart(cardProfile.getStatementClosingDay());
        LocalDate billingEnd = calculateBillingPeriodEnd(cardProfile.getStatementClosingDay());

        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                cardAsset.getHousehold().getId(),
                billingStart,
                billingEnd
        ).stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE && record.getAsset() != null && record.getAsset().getId().equals(cardAssetId))
                .map(TransactionDto::from)
                .toList();
    }

    // Private helper methods

    private BigDecimal calculateUnpaidAmount(Asset cardAsset) {
        BigDecimal cardExpenses = transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                cardAsset.getHousehold().getId(),
                LocalDate.of(1900, 1, 1),
                LocalDate.now().plusYears(100)
        ).stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE && 
                        record.getAsset() != null && record.getAsset().getId().equals(cardAsset.getId()))
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedPayments = cardPaymentScheduleRepository
                .findByCardAssetIdAndStatusOrderByScheduledDateAsc(cardAsset.getId(), PaymentStatus.COMPLETED)
                .stream()
                .map(CardPaymentSchedule::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return cardExpenses.subtract(completedPayments).max(BigDecimal.ZERO);
    }

    private BigDecimal calculatePaymentScheduleAmount(Asset cardAsset) {
        CardProfile cardProfile = cardAsset.getCardProfile();
        if (cardProfile == null) {
            return BigDecimal.ZERO;
        }

        LocalDate billingStart = calculateBillingPeriodStart(cardProfile.getStatementClosingDay());
        LocalDate billingEnd = calculateBillingPeriodEnd(cardProfile.getStatementClosingDay());

        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                cardAsset.getHousehold().getId(),
                billingStart,
                billingEnd
        ).stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE && 
                        record.getAsset() != null && record.getAsset().getId().equals(cardAsset.getId()))
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate calculateBillingPeriodStart(int statementClosingDay) {
        LocalDate today = LocalDate.now();
        LocalDate lastMonthClosingDay = LocalDate.of(today.getYear(), today.getMonth(), 1)
                .minusDays(1)
                .withDayOfMonth(Math.min(statementClosingDay, today.minusMonths(1).lengthOfMonth()));

        return lastMonthClosingDay.plusDays(1);
    }

    private LocalDate calculateBillingPeriodEnd(int statementClosingDay) {
        LocalDate today = LocalDate.now();
        int maxDayOfMonth = today.lengthOfMonth();
        int closingDay = Math.min(statementClosingDay, maxDayOfMonth);
        return today.withDayOfMonth(closingDay);
    }

    /**
     * 카드 결제 예약 생성
     */
    @Transactional
    public CardPaymentScheduleDto createPaymentSchedule(Long cardAssetId, LocalDate scheduledDate, BigDecimal amount) {
        if (scheduledDate == null) {
            throw new IllegalArgumentException("Payment scheduled date is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        Asset cardAsset = assetRepository.findById(cardAssetId).orElseThrow();
        if (cardAsset.getType() != AssetType.CARD) {
            throw new IllegalArgumentException("Card asset type expected");
        }

        CardPaymentSchedule schedule = new CardPaymentSchedule(cardAsset, scheduledDate, amount);
        CardPaymentSchedule saved = cardPaymentScheduleRepository.save(schedule);
        return CardPaymentScheduleDto.from(saved);
    }

    /**
     * 특정 카드의 모든 결제 예약 조회
     */
    @Transactional(readOnly = true)
    public List<CardPaymentScheduleDto> getPaymentSchedules(Long cardAssetId) {
        return cardPaymentScheduleRepository.findByCardAssetIdOrderByScheduledDateAsc(cardAssetId)
                .stream()
                .map(CardPaymentScheduleDto::from)
                .toList();
    }

    /**
     * 예정된 결제 목록 조회 (자동 결제 대상)
     */
    @Transactional(readOnly = true)
    public List<CardPaymentScheduleDto> getScheduledPayments(LocalDate upToDate) {
        return cardPaymentScheduleRepository.findByScheduledDateLessThanEqualAndStatusOrderByScheduledDateAsc(upToDate, PaymentStatus.SCHEDULED)
                .stream()
                .map(CardPaymentScheduleDto::from)
                .toList();
    }

    @Transactional
    public void cancelPaymentSchedule(Long scheduleId) {
        CardPaymentSchedule schedule = cardPaymentScheduleRepository.findById(scheduleId).orElseThrow();
        if (schedule.getStatus() != PaymentStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled payments can be cancelled");
        }
        cardPaymentScheduleRepository.delete(schedule);
    }

    /**
     * 결제 예약 실행 (거래 생성)
     */
    @Transactional
    public void executePaymentSchedule(Long scheduleId, LedgerService ledgerService) {
        CardPaymentSchedule schedule = cardPaymentScheduleRepository.findById(scheduleId).orElseThrow();
        
        if (schedule.getStatus() != PaymentStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled payments can be executed");
        }

        Asset cardAsset = schedule.getCardAsset();
        Asset paymentAccount = cardAsset.getCardProfile().getPaymentAccount();

        try {
            ledgerService.createTransaction(new CreateTransactionRequest(
                    TransactionType.TRANSFER,
                    schedule.getScheduledDate(),
                    schedule.getAmount(),
                    null,
                    null,
                    paymentAccount.getId(),
                    null,
                    "카드 자동 결제",
                    cardAsset.getName(),
                    0
            ));

            schedule.setStatus(PaymentStatus.COMPLETED);
            schedule.setCompletedAt(java.time.LocalDateTime.now());
            cardPaymentScheduleRepository.save(schedule);
        } catch (Exception e) {
            schedule.setStatus(PaymentStatus.FAILED);
            schedule.setFailureReason(e.getMessage());
            cardPaymentScheduleRepository.save(schedule);
            throw e;
        }
    }
}

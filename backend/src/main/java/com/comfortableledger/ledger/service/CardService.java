package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.CardPaymentSchedule;
import com.comfortableledger.ledger.domain.PaymentStatus;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CardProfileRepository;
import com.comfortableledger.ledger.repository.CardPaymentScheduleRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.dto.ApiDtos.CardDetailDto;
import com.comfortableledger.ledger.dto.ApiDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.dto.ApiDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.dto.ApiDtos.TransactionDto;
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
    private final KoreanHolidayCalendar holidayCalendar;
    private final TransactionCommandService transactionCommandService;

    public CardService(AssetRepository assetRepository, CardProfileRepository cardProfileRepository,
                       TransactionRepository transactionRepository, CardPaymentScheduleRepository cardPaymentScheduleRepository,
                       KoreanHolidayCalendar holidayCalendar,
                       TransactionCommandService transactionCommandService) {
        this.assetRepository = assetRepository;
        this.cardProfileRepository = cardProfileRepository;
        this.transactionRepository = transactionRepository;
        this.cardPaymentScheduleRepository = cardPaymentScheduleRepository;
        this.holidayCalendar = holidayCalendar;
        this.transactionCommandService = transactionCommandService;
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
        BillingCycle billingCycle = calculateBillingCycle(cardProfile, LocalDate.now());
        BigDecimal paymentScheduleAmount = calculatePaymentScheduleAmount(cardAsset, billingCycle);

        return new CardDetailDto(
                cardAsset.getId(),
                cardAsset.getName(),
                cardAsset.getBalance(),
                cardProfile.getPaymentAccount().getId(),
                cardProfile.getStatementClosingDay(),
                cardProfile.getPaymentDay(),
                cardProfile.isAutoPayment(),
                unpaidAmount,
                paymentScheduleAmount,
                billingCycle.paymentDate(),
                billingCycle.originalPaymentDate(),
                !billingCycle.paymentDate().equals(billingCycle.originalPaymentDate()),
                billingCycle.startDate(),
                billingCycle.endDate()
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
        CardProfile cardProfile = cardAsset.getCardProfile();
        if (cardProfile == null) {
            return BigDecimal.ZERO;
        }
        return calculatePaymentScheduleAmount(cardAsset, calculateBillingCycle(cardProfile, LocalDate.now()));
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

        BillingCycle billingCycle = calculateBillingCycle(cardProfile, LocalDate.now());

        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                cardAsset.getHousehold().getId(),
                billingCycle.startDate(),
                billingCycle.endDate()
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

    private BigDecimal calculatePaymentScheduleAmount(Asset cardAsset, BillingCycle billingCycle) {
        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                cardAsset.getHousehold().getId(),
                billingCycle.startDate(),
                billingCycle.endDate()
        ).stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE && 
                        record.getAsset() != null && record.getAsset().getId().equals(cardAsset.getId()))
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BillingCycle calculateBillingCycle(CardProfile cardProfile, LocalDate referenceDate) {
        LocalDate originalPaymentDate = adjustedDay(YearMonth.from(referenceDate), cardProfile.getPaymentDay());
        LocalDate paymentDate = holidayCalendar.nextBusinessDay(originalPaymentDate);
        if (paymentDate.isBefore(referenceDate)) {
            originalPaymentDate = adjustedDay(YearMonth.from(referenceDate).plusMonths(1), cardProfile.getPaymentDay());
            paymentDate = holidayCalendar.nextBusinessDay(originalPaymentDate);
        }

        YearMonth billingEndMonth = YearMonth.from(originalPaymentDate).minusMonths(1);
        LocalDate billingEndDate = adjustedDay(billingEndMonth, cardProfile.getStatementClosingDay());
        LocalDate billingStartDate = adjustedDay(billingEndMonth.minusMonths(1), cardProfile.getStatementClosingDay()).plusDays(1);
        return new BillingCycle(originalPaymentDate, paymentDate, billingStartDate, billingEndDate);
    }

    private LocalDate adjustedDay(YearMonth yearMonth, int day) {
        return yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth()));
    }

    private record BillingCycle(LocalDate originalPaymentDate, LocalDate paymentDate, LocalDate startDate, LocalDate endDate) {
    }

    /**
     * 移대뱶 寃곗젣 ?덉빟 ?앹꽦
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
     * ?뱀젙 移대뱶??紐⑤뱺 寃곗젣 ?덉빟 議고쉶
     */
    @Transactional(readOnly = true)
    public List<CardPaymentScheduleDto> getPaymentSchedules(Long cardAssetId) {
        return cardPaymentScheduleRepository.findByCardAssetIdOrderByScheduledDateAsc(cardAssetId)
                .stream()
                .map(CardPaymentScheduleDto::from)
                .toList();
    }

    /**
     * ?덉젙??寃곗젣 紐⑸줉 議고쉶 (?먮룞 寃곗젣 ???
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

    @Transactional
    public CardPaymentScheduleDto rescheduleFailedPayment(Long scheduleId) {
        CardPaymentSchedule schedule = cardPaymentScheduleRepository.findById(scheduleId).orElseThrow();
        if (schedule.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Only failed payments can be rescheduled");
        }
        schedule.reschedule();
        return CardPaymentScheduleDto.from(cardPaymentScheduleRepository.save(schedule));
    }

    @Transactional
    public CardPaymentScheduleDto retryFailedPayment(Long scheduleId) {
        rescheduleFailedPayment(scheduleId);
        return executePaymentSchedule(scheduleId);
    }

    /**
     * 寃곗젣 ?덉빟 ?ㅽ뻾 (嫄곕옒 ?앹꽦)
     */
    @Transactional
    public CardPaymentScheduleDto executePaymentSchedule(Long scheduleId) {
        CardPaymentSchedule schedule = cardPaymentScheduleRepository.findById(scheduleId).orElseThrow();
        
        if (schedule.getStatus() != PaymentStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled payments can be executed");
        }

        schedule.markProcessing();
        cardPaymentScheduleRepository.saveAndFlush(schedule);

        try {
            Asset cardAsset = schedule.getCardAsset();
            if (cardAsset.getCardProfile() == null || cardAsset.getCardProfile().getPaymentAccount() == null) {
                throw new IllegalStateException("Card payment account not configured");
            }
            Asset paymentAccount = cardAsset.getCardProfile().getPaymentAccount();

            transactionCommandService.createTransaction(new CreateTransactionRequest(
                    TransactionType.TRANSFER,
                    schedule.getScheduledDate(),
                    schedule.getAmount(),
                    null,
                    null,
                    paymentAccount.getId(),
                    null,
                    "移대뱶 ?먮룞 寃곗젣",
                    cardAsset.getName(),
                    null,
                    null,
                    null,
                    0
            ));

            schedule.markCompleted();
            cardPaymentScheduleRepository.save(schedule);
        } catch (RuntimeException e) {
            schedule.markFailed(failureMessage(e));
            cardPaymentScheduleRepository.save(schedule);
        }

        return CardPaymentScheduleDto.from(schedule);
    }

    private String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}

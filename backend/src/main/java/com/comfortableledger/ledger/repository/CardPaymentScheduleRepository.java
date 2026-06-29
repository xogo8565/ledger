package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.CardPaymentSchedule;
import com.comfortableledger.ledger.domain.PaymentStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardPaymentScheduleRepository extends JpaRepository<CardPaymentSchedule, Long> {
    /**
     * 특정 카드의 결제 예약 목록 조회
     */
    List<CardPaymentSchedule> findByCardAssetIdOrderByScheduledDateAsc(Long cardAssetId);

    /**
     * 특정 카드의 특정 상태 결제 예약 조회
     */
    List<CardPaymentSchedule> findByCardAssetIdAndStatusOrderByScheduledDateAsc(Long cardAssetId, PaymentStatus status);

    /**
     * 특정 날짜 이전의 예정된 결제 조회 (자동 결제)
     */
    List<CardPaymentSchedule> findByScheduledDateLessThanEqualAndStatusOrderByScheduledDateAsc(LocalDate date, PaymentStatus status);

    /**
     * 특정 카드의 특정 날짜 결제 예약 조회
     */
    List<CardPaymentSchedule> findByCardAssetIdAndScheduledDateOrderByIdAsc(Long cardAssetId, LocalDate date);
}

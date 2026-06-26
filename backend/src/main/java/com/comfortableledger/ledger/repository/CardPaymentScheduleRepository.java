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
     * ?뱀젙 移대뱶??寃곗젣 ?덉빟 紐⑸줉 議고쉶
     */
    List<CardPaymentSchedule> findByCardAssetIdOrderByScheduledDateAsc(Long cardAssetId);

    /**
     * ?뱀젙 移대뱶???뱀젙 ?곹깭 寃곗젣 ?덉빟 議고쉶
     */
    List<CardPaymentSchedule> findByCardAssetIdAndStatusOrderByScheduledDateAsc(Long cardAssetId, PaymentStatus status);

    /**
     * ?뱀젙 ?좎쭨 ?댁쟾???덉젙??寃곗젣 議고쉶 (?먮룞 寃곗젣??
     */
    List<CardPaymentSchedule> findByScheduledDateLessThanEqualAndStatusOrderByScheduledDateAsc(LocalDate date, PaymentStatus status);

    /**
     * ?뱀젙 移대뱶???뱀젙 ?좎쭨 寃곗젣 ?덉빟 議고쉶
     */
    List<CardPaymentSchedule> findByCardAssetIdAndScheduledDateOrderByIdAsc(Long cardAssetId, LocalDate date);
}

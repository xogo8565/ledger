package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KoreanHolidayCalendarTest {
    private final KoreanHolidayCalendar calendar = new KoreanHolidayCalendar();

    @Test
    void movesWeekendAndSubstituteHolidayToNextBusinessDay() {
        assertThat(calendar.nextBusinessDay(LocalDate.of(2026, 10, 3)))
                .isEqualTo(LocalDate.of(2026, 10, 6));
    }

    @Test
    void movesPublicHolidayToNextBusinessDay() {
        assertThat(calendar.nextBusinessDay(LocalDate.of(2026, 9, 25)))
                .isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void keepsBusinessDay() {
        assertThat(calendar.nextBusinessDay(LocalDate.of(2026, 9, 28)))
                .isEqualTo(LocalDate.of(2026, 9, 28));
    }
}

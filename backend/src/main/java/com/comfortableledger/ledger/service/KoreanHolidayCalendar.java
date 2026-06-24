package com.comfortableledger.ledger.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class KoreanHolidayCalendar {
    private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),
            MonthDay.of(3, 1),
            MonthDay.of(5, 1),
            MonthDay.of(5, 5),
            MonthDay.of(6, 6),
            MonthDay.of(8, 15),
            MonthDay.of(10, 3),
            MonthDay.of(10, 9),
            MonthDay.of(12, 25)
    );

    private static final Map<Integer, Set<LocalDate>> CALENDAR_HOLIDAYS = Map.of(
            2025, Set.of(
                    LocalDate.of(2025, 1, 27),
                    LocalDate.of(2025, 1, 28),
                    LocalDate.of(2025, 1, 29),
                    LocalDate.of(2025, 1, 30),
                    LocalDate.of(2025, 3, 3),
                    LocalDate.of(2025, 5, 6),
                    LocalDate.of(2025, 6, 3),
                    LocalDate.of(2025, 10, 5),
                    LocalDate.of(2025, 10, 6),
                    LocalDate.of(2025, 10, 7),
                    LocalDate.of(2025, 10, 8)
            ),
            2026, Set.of(
                    LocalDate.of(2026, 2, 16),
                    LocalDate.of(2026, 2, 17),
                    LocalDate.of(2026, 2, 18),
                    LocalDate.of(2026, 3, 2),
                    LocalDate.of(2026, 5, 24),
                    LocalDate.of(2026, 5, 25),
                    LocalDate.of(2026, 6, 3),
                    LocalDate.of(2026, 8, 17),
                    LocalDate.of(2026, 9, 24),
                    LocalDate.of(2026, 9, 25),
                    LocalDate.of(2026, 9, 26),
                    LocalDate.of(2026, 10, 5)
            ),
            2027, Set.of(
                    LocalDate.of(2027, 2, 6),
                    LocalDate.of(2027, 2, 7),
                    LocalDate.of(2027, 2, 8),
                    LocalDate.of(2027, 2, 9),
                    LocalDate.of(2027, 5, 13),
                    LocalDate.of(2027, 6, 7),
                    LocalDate.of(2027, 9, 14),
                    LocalDate.of(2027, 9, 15),
                    LocalDate.of(2027, 9, 16),
                    LocalDate.of(2027, 10, 4),
                    LocalDate.of(2027, 10, 11),
                    LocalDate.of(2027, 12, 27)
            )
    );

    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate candidate = date;
        while (isNonBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    public boolean isNonBusinessDay(LocalDate date) {
        return isWeekend(date) || isHoliday(date);
    }

    public boolean isHoliday(LocalDate date) {
        return FIXED_HOLIDAYS.contains(MonthDay.from(date))
                || CALENDAR_HOLIDAYS.getOrDefault(date.getYear(), Set.of()).contains(date);
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}

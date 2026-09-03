package io.multiagent.core.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record DateRange(LocalDate start, LocalDate end) {

    public long getDays() {
        return ChronoUnit.DAYS.between(start, end);
    }

    public DateRange limitTo30Days() {
        LocalDate limitedStart = end.minusDays(30);
        return new DateRange(limitedStart, end);
    }
}

package io.multiagent.core.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Component
public class DateProvider {

    @Value("${ai-core.reference-date:}")
    private String referenceDate;

    public LocalDate todayUtc() {
        if (referenceDate != null && !referenceDate.isBlank()) {
            try {
                return LocalDate.parse(referenceDate);
            } catch (DateTimeParseException ignored) {
                // fallback below
            }
        }
        return LocalDate.now(ZoneOffset.UTC);
    }
}

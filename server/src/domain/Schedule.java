package domain;

import java.time.LocalDate;

// DB에서 조회한 일정 한 건을 담는다.
public record Schedule(
        long scheduleId,
        LocalDate scheduleDate,
        String title,
        String details
) {
}
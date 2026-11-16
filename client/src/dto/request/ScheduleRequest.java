package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;

public class ScheduleRequest extends DTO {

    private final String payload;

    private ScheduleRequest(String payload) {
        super(DtoType.SCHEDULE);
        this.payload = payload;
    }

    public static ScheduleRequest list(YearMonth month) {
        return new ScheduleRequest("LIST," + month);
    }

    public static ScheduleRequest create(
            LocalDate date, String title, String details) {
        return new ScheduleRequest(
                "CREATE," + date + ","
                + encode(title) + "," + encode(details));
    }

    public static ScheduleRequest update(
            long id, LocalDate date, String title, String details) {
        return new ScheduleRequest(
                "UPDATE," + id + "," + date + ","
                + encode(title) + "," + encode(details));
    }

    public static ScheduleRequest delete(long id) {
        return new ScheduleRequest("DELETE," + id);
    }

    private static String encode(String text) {
        // 제목과 내용에 쉼표나 줄바꿈이 있어도 요청 형식을 유지한다.
        return Base64.getEncoder().encodeToString(
                text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return super.toString() + payload;
    }
}
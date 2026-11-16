package service;

import dao.ScheduleDao;
import domain.Schedule;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;

public class ScheduleService {

    private final ScheduleDao dao = new ScheduleDao();

    public String handle(int userId, String message) {
        String[] values = message.split(",", -1);
        String action = values.length == 0 ? "" : values[0];

        try {
            return switch (action) {
                case "LIST" -> list(userId, values);
                case "CREATE" -> create(userId, values);
                case "UPDATE" -> update(userId, values);
                case "DELETE" -> delete(userId, values);
                default -> "UNKNOWN,INVALID,";
            };
        } catch (DateTimeException | IllegalArgumentException exception) {
            // 잘못된 날짜·번호·Base64 값은 요청 오류로 처리한다.
            return action + ",INVALID,";
        } catch (SQLException exception) {
            // SQL 문장이나 사용자 입력을 로그에 남기지 않는다.
            System.err.println("일정 DB 오류 코드: "
                    + exception.getErrorCode());
            return action + ",ERROR,";
        }
    }

    private String list(int userId, String[] values)
            throws SQLException {

        if (values.length != 2) {
            return "LIST,INVALID,,";
        }

        YearMonth month = YearMonth.parse(values[1]);
        if (!validYear(month.getYear())) {
            return "LIST,INVALID,,";
        }

        StringBuilder rows = new StringBuilder();

        for (Schedule schedule : dao.findByMonth(userId, month)) {
            if (rows.length() > 0) {
                rows.append("|");
            }

            // 한 행: 번호,날짜,Base64(제목),Base64(내용)
            rows.append(schedule.scheduleId())
                    .append(",")
                    .append(schedule.scheduleDate())
                    .append(",")
                    .append(encode(schedule.title()))
                    .append(",")
                    .append(encode(schedule.details() == null
                            ? "" : schedule.details()));
        }

        return "LIST,OK," + month + "," + rows;
    }

    private String create(int userId, String[] values)
            throws SQLException {

        // 요청: CREATE,날짜,Base64(제목),Base64(내용)
        if (values.length != 4) {
            return "CREATE,INVALID,";
        }

        LocalDate date = LocalDate.parse(values[1]);
        String title = decode(values[2]).strip();
        String details = decode(values[3]);

        if (!validDate(date) || !validContent(title, details)) {
            return "CREATE,INVALID,";
        }

        long id = dao.insert(userId, date, title, details);
        return "CREATE,OK," + id;
    }

    private String update(int userId, String[] values)
            throws SQLException {

        // 요청: UPDATE,일정번호,날짜,Base64(제목),Base64(내용)
        if (values.length != 5) {
            return "UPDATE,INVALID,";
        }

        long id = Long.parseLong(values[1]);
        LocalDate date = LocalDate.parse(values[2]);
        String title = decode(values[3]).strip();
        String details = decode(values[4]);

        if (id <= 0 || !validDate(date)
                || !validContent(title, details)) {
            return "UPDATE,INVALID,";
        }

        // DAO의 WHERE 절에 user_id가 있어 자신의 일정만 수정된다.
        boolean updated = dao.update(
                userId, id, date, title, details);
        return updated
                ? "UPDATE,OK,"
                : "UPDATE,NOT_FOUND,";
    }

    private String delete(int userId, String[] values)
            throws SQLException {

        // 요청: DELETE,일정번호
        if (values.length != 2) {
            return "DELETE,INVALID,";
        }

        long id = Long.parseLong(values[1]);
        if (id <= 0) {
            return "DELETE,INVALID,";
        }

        // 없는 일정과 다른 회원의 일정은 같은 결과로 응답한다.
        boolean deleted = dao.delete(userId, id);
        return deleted
                ? "DELETE,OK,"
                : "DELETE,NOT_FOUND,";
    }

    private boolean validDate(LocalDate date) {
        return validYear(date.getYear());
    }

    private boolean validYear(int year) {
        // MySQL DATE가 안정적으로 처리할 수 있는 연도를 사용한다.
        return year >= 1000 && year <= 9999;
    }

    private boolean validContent(String title, String details) {
        return !title.isEmpty()
                && title.length() <= 100
                && title.codePoints()
                        .noneMatch(Character::isISOControl)
                && details.length() <= 2000
                && details.indexOf('\0') < 0;
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
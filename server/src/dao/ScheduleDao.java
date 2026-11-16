package dao;

import db.DBConnection;
import domain.Schedule;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class ScheduleDao {

    public List<Schedule> findByMonth(int userId, YearMonth month)
            throws SQLException {

        String sql = """
                SELECT schedule_id, schedule_date, title, details
                FROM schedules
                WHERE user_id = ?
                  AND schedule_date >= ?
                  AND schedule_date < ?
                ORDER BY schedule_date, schedule_id
                """;

        LocalDate firstDay = month.atDay(1);
        LocalDate nextMonth = month.plusMonths(1).atDay(1);
        List<Schedule> schedules = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            // 로그인한 회원의 일정과 요청한 달만 조회한다.
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(firstDay));
            statement.setDate(3, Date.valueOf(nextMonth));

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    schedules.add(new Schedule(
                            result.getLong("schedule_id"),
                            result.getDate("schedule_date").toLocalDate(),
                            result.getString("title"),
                            result.getString("details")));
                }
            }
        }

        return schedules;
    }

    public long insert(
            int userId,
            LocalDate date,
            String title,
            String details) throws SQLException {

        String sql = """
                INSERT INTO schedules
                    (user_id, schedule_date, title, details)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(date));
            statement.setString(3, title);
            statement.setString(4, details);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("일정 등록에 실패했습니다.");
            }

            // 등록 직후 화면을 갱신할 때 사용할 일정 번호를 반환한다.
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new SQLException("등록된 일정 번호를 확인하지 못했습니다.");
    }

    public boolean update(
            int userId,
            long scheduleId,
            LocalDate date,
            String title,
            String details) throws SQLException {

        String sql = """
                UPDATE schedules
                SET schedule_date = ?,
                    title = ?,
                    details = ?
                WHERE schedule_id = ?
                  AND user_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setDate(1, Date.valueOf(date));
            statement.setString(2, title);
            statement.setString(3, details);
            statement.setLong(4, scheduleId);

            // 일정 번호만으로 수정하지 않고 회원 번호도 함께 검사한다.
            statement.setInt(5, userId);

            return statement.executeUpdate() == 1;
        }
    }

    public boolean delete(int userId, long scheduleId)
            throws SQLException {

        String sql = """
                DELETE FROM schedules
                WHERE schedule_id = ?
                  AND user_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, scheduleId);

            // 다른 회원의 일정은 삭제할 수 없다.
            statement.setInt(2, userId);

            return statement.executeUpdate() == 1;
        }
    }
}
package dao;

import db.DBConnection;
import domain.Member;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserDao {

    // 비밀번호 원문이 아닌, 해시 처리된 값을 전달받음.
    public int insert(String loginId, String passwordHash,
                      String nickname) throws SQLException {
        String sql = """
                INSERT INTO users (login_id, password_hash, nickname)
                VALUES (?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, loginId);
            statement.setString(2, passwordHash);
            statement.setString(3, nickname);

            return statement.executeUpdate();
        }
    }

    public Optional<Member> findByLoginId(String loginId)
            throws SQLException {
        String sql = """
                SELECT user_id, login_id, nickname, created_at
                FROM users
                WHERE login_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, loginId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                Member member = new Member(
                        result.getInt("user_id"),
                        result.getString("login_id"),
                        result.getString("nickname"),
                        result.getTimestamp("created_at").toLocalDateTime()
                );

                return Optional.of(member);
            }
        }
    }
}
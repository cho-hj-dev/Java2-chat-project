package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://127.0.0.1:3307/java_chat?serverTimezone=Asia/Seoul";

    public static Connection getConnection() throws SQLException {
        String password = System.getenv("DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new SQLException("DB_PASSWORD 환경 변수를 설정해 주세요.");
        }

        return DriverManager.getConnection(URL, "root", password);
    }
}

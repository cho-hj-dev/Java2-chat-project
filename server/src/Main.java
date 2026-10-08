import app.Application;
import db.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) {
        try (Connection connection = DBConnection.getConnection()) {
            System.out.println("DB 연결 성공: " + connection.getCatalog());
        } catch (SQLException e) {
            System.err.println("DB 연결 실패: 서버를 시작하지 않습니다.");
            e.printStackTrace();
            return;
        }

        new Application();
    }
}
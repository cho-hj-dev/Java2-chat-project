package test;

import dao.UserDao;
import security.PasswordHasher;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

public class MemberDuplicateTest {

    public static void main(String[] args) throws Exception {
        UserDao userDao = new UserDao();
        String loginId = "week04_test01";

        if (userDao.findByLoginId(loginId).isEmpty()) {
            throw new IllegalStateException(
                    "먼저 MemberDbTest를 실행하여 테스트 회원을 등록하세요.");
        }

        char[] password = UUID.randomUUID().toString().toCharArray();
        String passwordHash;

        try {
            passwordHash = PasswordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }

        try {
            userDao.insert(loginId, passwordHash, "중복등록테스트");
            throw new IllegalStateException(
                    "테스트 실패: 중복 아이디가 등록됐습니다.");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062
                    && "23000".equals(e.getSQLState())) {
                System.out.println("중복 아이디 등록 차단 확인");
                System.out.println("MySQL 오류 코드: " + e.getErrorCode());
                System.out.println("UNIQUE 제약 검증 성공");
            } else {
                throw e;
            }
        }
    }
}
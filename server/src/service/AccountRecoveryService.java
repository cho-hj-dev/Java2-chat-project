package service;

import db.DBConnection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AccountRecoveryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private byte[] digest(long id, String email, String code)
            throws Exception {
        String secret = System.getenv("MAIL_CODE_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "MAIL_CODE_SECRET 환경변수를 확인하세요.");
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));

        String value = id + ":FIND_ID:" + email + ":" + code;
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    public long sendFindIdCode(String inputEmail) throws Exception {
        String email = SignupEmailService.normalizeEmail(inputEmail);

        if (!SignupEmailService.validEmail(email)) {
            throw new IllegalArgumentException("이메일 형식 오류");
        }

        // DB에 기록을 만들기 전에 필수 설정을 검사한다.
        String secret = System.getenv("MAIL_CODE_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "MAIL_CODE_SECRET 환경변수를 확인하세요.");
        }

        String code = String.format(
                "%06d", RANDOM.nextInt(1_000_000));
        long verificationId;

        try (Connection connection = DBConnection.getConnection()) {
            String recentSql = """
                    SELECT COUNT(*)
                    FROM email_verifications
                    WHERE email = ?
                      AND purpose = 'FIND_ID'
                      AND created_at > NOW() - INTERVAL 1 MINUTE
                    """;

            try (PreparedStatement statement =
                    connection.prepareStatement(recentSql)) {
                statement.setString(1, email);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) > 0) {
                        throw new IllegalStateException("RATE_LIMIT");
                    }
                }
            }

            String insertSql = """
                    INSERT INTO email_verifications
                        (email, purpose, code_hash, expires_at)
                    VALUES (?, 'FIND_ID', ?,
                            DATE_ADD(NOW(), INTERVAL 10 MINUTE))
                    """;

            try (PreparedStatement statement =
                    connection.prepareStatement(
                            insertSql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, email);
                statement.setString(2, "PENDING");
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException(
                                "인증 요청 번호 생성 실패");
                    }
                    verificationId = keys.getLong(1);
                }
            }

            String codeHash = HexFormat.of().formatHex(
                    digest(verificationId, email, code));

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                            UPDATE email_verifications
                            SET code_hash = ?
                            WHERE verification_id = ?
                            """)) {
                statement.setString(1, codeHash);
                statement.setLong(2, verificationId);
                statement.executeUpdate();
            }
        }

        try {
            new GmailSender().sendCode(email, code);
        } catch (Exception e) {
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                                 UPDATE email_verifications
                                 SET consumed_at = NOW()
                                 WHERE verification_id = ?
                                 """)) {
                statement.setLong(1, verificationId);
                statement.executeUpdate();
            }
            throw e;
        }

        return verificationId;
    }

    // null: 번호 오류·만료, 빈 문자열: 인증 성공했지만 가입 계정 없음
    // 그 외: 확인된 계정의 로그인 아이디
    public String verifyFindIdCode(
            long verificationId, String inputEmail, String code)
            throws Exception {

        String email = SignupEmailService.normalizeEmail(inputEmail);

        if (verificationId <= 0
                || !SignupEmailService.validEmail(email)
                || !code.matches("[0-9]{6}")) {
            return null;
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                byte[] expected;

                try (PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT code_hash
                                FROM email_verifications
                                WHERE verification_id = ?
                                  AND email = ?
                                  AND purpose = 'FIND_ID'
                                  AND consumed_at IS NULL
                                  AND expires_at > NOW()
                                  AND attempt_count < 5
                                FOR UPDATE
                                """)) {
                    statement.setLong(1, verificationId);
                    statement.setString(2, email);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return null;
                        }
                        expected = HexFormat.of().parseHex(
                                result.getString("code_hash"));
                    }
                }

                byte[] actual = digest(
                        verificationId, email, code);

                if (!MessageDigest.isEqual(expected, actual)) {
                    try (PreparedStatement statement =
                            connection.prepareStatement("""
                                    UPDATE email_verifications
                                    SET attempt_count = attempt_count + 1
                                    WHERE verification_id = ?
                                    """)) {
                        statement.setLong(1, verificationId);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return null;
                }

                try (PreparedStatement statement =
                        connection.prepareStatement("""
                                UPDATE email_verifications
                                SET consumed_at = NOW()
                                WHERE verification_id = ?
                                  AND consumed_at IS NULL
                                """)) {
                    statement.setLong(1, verificationId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return null;
                    }
                }

                String loginId = "";
                try (PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT login_id
                                FROM users
                                WHERE email = ?
                                  AND email_verified_at IS NOT NULL
                                """)) {
                    statement.setString(1, email);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            loginId = result.getString("login_id");
                        }
                    }
                }

                connection.commit();
                return loginId;

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }
    
    private byte[] resetDigest(
            long id, String loginId, String email, String code)
            throws Exception {

        String secret = System.getenv("MAIL_CODE_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "MAIL_CODE_SECRET 환경변수를 확인하세요.");
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));

        String value = id + ":RESET_PASSWORD:"
                + loginId + ":" + email + ":" + code;
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    public long sendResetCode(String inputLoginId, String inputEmail)
            throws Exception {

        String loginId = inputLoginId.trim();
        String email = SignupEmailService.normalizeEmail(inputEmail);

        if (!loginId.matches("[A-Za-z0-9_]{4,20}")
                || !SignupEmailService.validEmail(email)) {
            throw new IllegalArgumentException("입력 형식 오류");
        }

        String secret = System.getenv("MAIL_CODE_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "MAIL_CODE_SECRET 환경변수를 확인하세요.");
        }

        String code = String.format(
                "%06d", RANDOM.nextInt(1_000_000));
        long verificationId;
        Integer userId = null;

        try (Connection connection = DBConnection.getConnection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("""
                            SELECT user_id
                            FROM users
                            WHERE login_id = ?
                              AND email = ?
                              AND email_verified_at IS NOT NULL
                            """)) {
                statement.setString(1, loginId);
                statement.setString(2, email);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        userId = result.getInt("user_id");
                    }
                }
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                            SELECT COUNT(*)
                            FROM email_verifications
                            WHERE email = ?
                              AND purpose = 'RESET_PASSWORD'
                              AND created_at >
                                  NOW() - INTERVAL 1 MINUTE
                            """)) {
                statement.setString(1, email);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) > 0) {
                        throw new IllegalStateException("RATE_LIMIT");
                    }
                }
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                            INSERT INTO email_verifications
                                (user_id, email, purpose,
                                 code_hash, expires_at)
                            VALUES (?, ?, 'RESET_PASSWORD', ?,
                                    DATE_ADD(NOW(), INTERVAL 10 MINUTE))
                            """, Statement.RETURN_GENERATED_KEYS)) {

                if (userId == null) {
                    statement.setNull(1, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(1, userId);
                }
                statement.setString(2, email);
                statement.setString(3, "PENDING");
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException(
                                "인증 요청 번호 생성 실패");
                    }
                    verificationId = keys.getLong(1);
                }
            }

            String codeHash = HexFormat.of().formatHex(
                    resetDigest(verificationId, loginId, email, code));

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                            UPDATE email_verifications
                            SET code_hash = ?
                            WHERE verification_id = ?
                            """)) {
                statement.setString(1, codeHash);
                statement.setLong(2, verificationId);
                statement.executeUpdate();
            }
        }

        // 일치하지 않는 아이디·이메일에도 같은 발송 응답을 준다.
        // 실제 메일은 확인된 계정에만 보낸다.
        if (userId == null) {
            return verificationId;
        }

        try {
            new GmailSender().sendCode(email, code);
        } catch (Exception e) {
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                                 UPDATE email_verifications
                                 SET consumed_at = NOW()
                                 WHERE verification_id = ?
                                 """)) {
                statement.setLong(1, verificationId);
                statement.executeUpdate();
            }
            throw e;
        }

        return verificationId;
    }

    public String resetPassword(
            long verificationId, String inputLoginId,
            String inputEmail, String code, String newPassword)
            throws Exception {

        String loginId = inputLoginId.trim();
        String email = SignupEmailService.normalizeEmail(inputEmail);

        if (verificationId <= 0
                || !loginId.matches("[A-Za-z0-9_]{4,20}")
                || !SignupEmailService.validEmail(email)
                || !code.matches("[0-9]{6}")
                || !newPassword.matches(
                        "^(?=.*[A-Za-z])(?=.*[0-9])"
                        + "(?=.*[^A-Za-z0-9\\s])\\S{8,20}$")) {
            return "INVALID_INPUT";
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                byte[] expected;
                int userId;

                try (PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT code_hash, user_id
                                FROM email_verifications
                                WHERE verification_id = ?
                                  AND email = ?
                                  AND purpose = 'RESET_PASSWORD'
                                  AND consumed_at IS NULL
                                  AND expires_at > NOW()
                                  AND attempt_count < 5
                                FOR UPDATE
                                """)) {
                    statement.setLong(1, verificationId);
                    statement.setString(2, email);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()
                                || result.getObject("user_id") == null) {
                            connection.rollback();
                            return "INVALID_CODE";
                        }
                        expected = HexFormat.of().parseHex(
                                result.getString("code_hash"));
                        userId = result.getInt("user_id");
                    }
                }

                byte[] actual = resetDigest(
                        verificationId, loginId, email, code);

                if (!MessageDigest.isEqual(expected, actual)) {
                    try (PreparedStatement statement =
                            connection.prepareStatement("""
                                    UPDATE email_verifications
                                    SET attempt_count = attempt_count + 1
                                    WHERE verification_id = ?
                                    """)) {
                        statement.setLong(1, verificationId);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return "INVALID_CODE";
                }

                char[] passwordChars = newPassword.toCharArray();
                String passwordHash;
                try {
                    passwordHash =
                            security.PasswordHasher.hash(passwordChars);
                } finally {
                    java.util.Arrays.fill(passwordChars, '\0');
                }

                try (PreparedStatement statement =
                        connection.prepareStatement("""
                                UPDATE users
                                SET password_hash = ?
                                WHERE user_id = ?
                                  AND login_id = ?
                                  AND email = ?
                                  AND email_verified_at IS NOT NULL
                                """)) {
                    statement.setString(1, passwordHash);
                    statement.setInt(2, userId);
                    statement.setString(3, loginId);
                    statement.setString(4, email);

                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return "INVALID_CODE";
                    }
                }

                try (PreparedStatement statement =
                        connection.prepareStatement("""
                                UPDATE email_verifications
                                SET consumed_at = NOW()
                                WHERE verification_id = ?
                                  AND consumed_at IS NULL
                                """)) {
                    statement.setLong(1, verificationId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return "INVALID_CODE";
                    }
                }

                connection.commit();
                return "SUCCESS";

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
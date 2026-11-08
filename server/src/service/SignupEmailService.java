package service;

import db.DBConnection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SignupEmailService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validEmail(String email) {
        return email != null
                && email.length() <= 254
                && EMAIL_PATTERN.matcher(email).matches();
    }

    private static byte[] codeDigest(
            long verificationId, String email, String code)
            throws Exception {

        String secret = System.getenv("MAIL_CODE_SECRET");

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "MAIL_CODE_SECRET 환경변수를 설정하세요."
            );
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));

        String value = verificationId + ":SIGNUP:" + email + ":" + code;

        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    public long sendSignupCode(String inputEmail) throws Exception {
    	String secret = System.getenv("MAIL_CODE_SECRET");
    	if (secret == null || secret.length() < 32) {
    	    throw new IllegalStateException(
    	            "MAIL_CODE_SECRET 환경변수를 설정하세요."
    	    );
    	}
        String email = normalizeEmail(inputEmail);

        if (!validEmail(email)) {
            throw new IllegalArgumentException("이메일 형식 오류");
        }

        String code = String.format(
                "%06d",
                RANDOM.nextInt(1_000_000)
        );

        long verificationId;

        try (Connection connection = DBConnection.getConnection()) {

            // 같은 이메일로 너무 자주 요청하는 것을 제한
            String recentSql = """
                    SELECT COUNT(*)
                    FROM email_verifications
                    WHERE email = ?
                      AND purpose = 'SIGNUP'
                      AND created_at > NOW() - INTERVAL 1 MINUTE
                    """;

            try (PreparedStatement statement =
                    connection.prepareStatement(recentSql)) {

                statement.setString(1, email);

                try (ResultSet result = statement.executeQuery()) {
                    result.next();

                    if (result.getInt(1) > 0) {
                        throw new IllegalStateException(
                                "인증번호는 1분 후 다시 요청할 수 있습니다."
                        );
                    }
                }
            }

            String insertSql = """
                    INSERT INTO email_verifications
                        (email, purpose, code_hash, expires_at)
                    VALUES (?, 'SIGNUP', ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE))
                    """;

            // 코드에는 아직 verification_id가 필요하므로
            // 먼저 행을 만들고 생성된 번호로 검증값을 갱신
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            insertSql,
                            Statement.RETURN_GENERATED_KEYS
                    )) {

                statement.setString(1, email);
                statement.setString(2, "PENDING");
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException(
                                "인증 요청 번호 생성 실패"
                        );
                    }

                    verificationId = keys.getLong(1);
                }
            }

            String digest = HexFormat.of().formatHex(
                    codeDigest(verificationId, email, code)
            );

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                            UPDATE email_verifications
                            SET code_hash = ?
                            WHERE verification_id = ?
                            """)) {

                statement.setString(1, digest);
                statement.setLong(2, verificationId);
                statement.executeUpdate();
            }
        }

        try {
            new GmailSender().sendCode(email, code);
        } catch (Exception e) {
            // 메일 발송에 실패한 인증번호는 사용하지 못하게 처리
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

    public boolean verifySignupCode(
            long verificationId, String inputEmail, String code)
            throws Exception {

        String email = normalizeEmail(inputEmail);

        if (!validEmail(email) || !code.matches("[0-9]{6}")) {
            return false;
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                byte[] expected = null;

                String selectSql = """
                        SELECT code_hash
                        FROM email_verifications
                        WHERE verification_id = ?
                          AND email = ?
                          AND purpose = 'SIGNUP'
                          AND consumed_at IS NULL
                          AND expires_at > NOW()
                          AND attempt_count < 5
                        FOR UPDATE
                        """;

                try (PreparedStatement statement =
                        connection.prepareStatement(selectSql)) {

                    statement.setLong(1, verificationId);
                    statement.setString(2, email);

                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            expected = HexFormat.of().parseHex(
                                    result.getString("code_hash")
                            );
                        }
                    }
                }

                if (expected == null) {
                    connection.rollback();
                    return false;
                }

                byte[] actual = codeDigest(
                        verificationId, email, code
                );

                boolean matched = MessageDigest.isEqual(
                        expected, actual
                );

                if (!matched) {
                    try (PreparedStatement statement =
                            connection.prepareStatement("""
                                    UPDATE email_verifications
                                    SET attempt_count = attempt_count + 1
                                    WHERE verification_id = ?
                                    """)) {

                        statement.setLong(1, verificationId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                return matched;

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }
    
 // 호출한 쪽에서 트랜잭션을 시작하고 commit/rollback해야 한다.
    public boolean consumeSignupCode(
            Connection connection, long verificationId,
            String inputEmail, String code) throws Exception {

        String email = normalizeEmail(inputEmail);
        if (verificationId <= 0 || !validEmail(email)
                || !code.matches("[0-9]{6}")) {
            return false;
        }

        String selectSql = """
                SELECT code_hash
                FROM email_verifications
                WHERE verification_id = ?
                  AND email = ?
                  AND purpose = 'SIGNUP'
                  AND consumed_at IS NULL
                  AND expires_at > NOW()
                  AND attempt_count < 5
                FOR UPDATE
                """;

        byte[] expected;
        try (PreparedStatement statement =
                connection.prepareStatement(selectSql)) {
            statement.setLong(1, verificationId);
            statement.setString(2, email);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                expected = HexFormat.of().parseHex(
                        result.getString("code_hash"));
            }
        }

        byte[] actual = codeDigest(verificationId, email, code);
        if (!MessageDigest.isEqual(expected, actual)) {
            return false;
        }

        String updateSql = """
                UPDATE email_verifications
                SET consumed_at = NOW()
                WHERE verification_id = ?
                  AND consumed_at IS NULL
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(updateSql)) {
            statement.setLong(1, verificationId);
            return statement.executeUpdate() == 1;
        }
    }
}
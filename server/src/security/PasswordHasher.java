package security;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {

    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(char[] password)
            throws GeneralSecurityException {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("비밀번호가 비어 있습니다.");
        }

        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        PBEKeySpec spec =
                new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            Base64.Encoder encoder = Base64.getEncoder();

            return "pbkdf2_sha256$" + ITERATIONS
                    + "$" + encoder.encodeToString(salt)
                    + "$" + encoder.encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }
    
    //반환값이 일치하면 true,  다르거나 저장 형식이 잘못되면 false
    // 암호화 알고리즘 실행 자체에 문제가 생기면 예외를 호츌한 쪽으로 전달, 서버에서 처리 오류와 인증 실패를 구분
    public static boolean verify(char[] password, String storedHash)
            throws GeneralSecurityException {
        if (password == null || password.length == 0
                || storedHash == null || storedHash.length() > 255) {
            return false;
        }

        try {
            String[] parts = storedHash.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > 2_000_000) {
                return false;
            }

            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expectedHash = decoder.decode(parts[3]);

            if (salt.length != 16 || expectedHash.length != KEY_LENGTH / 8) {
                return false;
            }

            PBEKeySpec spec =
                    new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            byte[] actualHash = null;

            try {
                SecretKeyFactory factory =
                        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                actualHash = factory.generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expectedHash, actualHash);
            } finally {
                spec.clearPassword();
                if (actualHash != null) {
                    Arrays.fill(actualHash, (byte) 0);
                }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
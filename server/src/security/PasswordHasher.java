package security;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

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
}
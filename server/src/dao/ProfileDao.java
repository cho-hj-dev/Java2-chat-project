package dao;

import db.DBConnection;
import security.PasswordHasher;
import storage.ProfileImageStore;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

public class ProfileDao {

    public String update(
            int memberId,
            String nickname,
            String postalCode,
            String address,
            String addressDetail,
            String imageAction,
            String imageData,
            String currentPassword,
            String newPassword
    ) throws SQLException, IOException, GeneralSecurityException {

        nickname = nickname.trim();
        postalCode = postalCode.trim();
        address = address.trim();
        addressDetail = addressDetail.trim();

        // 기존 채팅 메시지 구분자로 사용하는 문자도 제외
        if (!nickname.matches("[^,:|+\\p{Cntrl}]{1,50}")) {
            return "INVALID_NICKNAME";
        }

        boolean hasAddress =
                !postalCode.isEmpty()
                || !address.isEmpty()
                || !addressDetail.isEmpty();

        if (hasAddress
                && (!postalCode.matches("[0-9]{5}")
                || address.isEmpty())) {
            return "INVALID_ADDRESS";
        }

        if (address.length() > 255
                || addressDetail.length() > 255
                || Pattern.compile("\\p{Cntrl}")
                        .matcher(address + addressDetail).find()) {
            return "INVALID_ADDRESS";
        }

        boolean changePassword =
                !currentPassword.isEmpty() || !newPassword.isEmpty();

        if (changePassword) {
            if (currentPassword.isEmpty()
                    || currentPassword.length() > 20) {
                return "WRONG_PASSWORD";
            }

            if (!newPassword.matches(
                    "^(?=.*[A-Za-z])(?=.*[0-9])"
                    + "(?=.*[^A-Za-z0-9\\s])\\S{8,20}$")) {
                return "INVALID_PASSWORD";
            }
        }

        if (!imageAction.equals("KEEP")
                && !imageAction.equals("REMOVE")
                && !imageAction.equals("REPLACE")) {
            return "INVALID_IMAGE";
        }

        if (imageAction.equals("REPLACE") && imageData.isEmpty()) {
            return "INVALID_IMAGE";
        }

        if (!imageAction.equals("REPLACE") && !imageData.isEmpty()) {
            return "INVALID_IMAGE";
        }

        // DB 변경 전에 새 사진 데이터 검증
        java.awt.image.BufferedImage replacementImage = null;

        if (imageAction.equals("REPLACE")) {
            try {
                replacementImage = ProfileImageStore.decode(imageData);
            } catch (IllegalArgumentException e) {
                return "INVALID_IMAGE";
            }
        }

        String oldImagePath = null;
        String createdImagePath = null;
        String finalImagePath = null;
        boolean committed = false;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String storedHash;

                // 현재 로그인한 회원의 행을 잠그고 조회
                String selectSql = """
                        SELECT password_hash, profile_image_path
                        FROM users
                        WHERE user_id = ?
                        FOR UPDATE
                        """;

                try (PreparedStatement statement =
                        connection.prepareStatement(selectSql)) {

                    statement.setInt(1, memberId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return "NOT_FOUND";
                        }

                        storedHash = result.getString("password_hash");
                        oldImagePath =
                                result.getString("profile_image_path");
                    }
                }

                // 비밀번호 변경 요청이 있을 때만 현재 비밀번호 확인
                String newHash = null;

                if (changePassword) {
                    char[] currentChars = currentPassword.toCharArray();
                    char[] newChars = newPassword.toCharArray();

                    try {
                        if (!PasswordHasher.verify(
                                currentChars, storedHash)) {
                            connection.rollback();
                            return "WRONG_PASSWORD";
                        }

                        newHash = PasswordHasher.hash(newChars);
                    } finally {
                        Arrays.fill(currentChars, '\0');
                        Arrays.fill(newChars, '\0');
                    }
                }

                // 본인의 현재 닉네임은 중복 대상에서 제외
                String duplicateSql = """
                        SELECT user_id
                        FROM users
                        WHERE nickname = ? AND user_id <> ?
                        LIMIT 1
                        """;

                try (PreparedStatement statement =
                        connection.prepareStatement(duplicateSql)) {

                    statement.setString(1, nickname);
                    statement.setInt(2, memberId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return "DUPLICATE_NICKNAME";
                        }
                    }
                }

                finalImagePath = oldImagePath;

                if (imageAction.equals("REMOVE")) {
                    finalImagePath = null;
                } else if (imageAction.equals("REPLACE")) {
                    createdImagePath =
                            ProfileImageStore.save(replacementImage);
                    finalImagePath = createdImagePath;
                }

                // 모든 변경 사항을 한 번에 저장
                String updateSql = """
                        UPDATE users
                        SET nickname = ?,
                            postal_code = ?,
                            address = ?,
                            address_detail = ?,
                            profile_image_path = ?,
                            password_hash = COALESCE(?, password_hash)
                        WHERE user_id = ?
                        """;

                try (PreparedStatement statement =
                        connection.prepareStatement(updateSql)) {

                    statement.setString(1, nickname);
                    statement.setString(2, emptyToNull(postalCode));
                    statement.setString(3, emptyToNull(address));
                    statement.setString(4, emptyToNull(addressDetail));
                    statement.setString(5, finalImagePath);
                    statement.setString(6, newHash);
                    statement.setInt(7, memberId);

                    statement.executeUpdate();
                }

                connection.commit();
                committed = true;

            } catch (SQLException e) {
                connection.rollback();

                // UNIQUE 제약조건에 의한 동시 중복 변경도 차단
                if (e.getErrorCode() == 1062) {
                    return "DUPLICATE_NICKNAME";
                }

                throw e;

            } catch (IOException | GeneralSecurityException
                    | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } finally {
            if (!committed) {
                // DB 저장 실패 시 새로 만든 사진만 정리
                deleteQuietly(createdImagePath);
            } else if (!Objects.equals(oldImagePath, finalImagePath)) {
                // DB 저장 성공 후에만 이전 사진 정리
                deleteQuietly(oldImagePath);
            }
        }

        return "SUCCESS";
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static void deleteQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }

        try {
            ProfileImageStore.delete(path);
        } catch (IOException | IllegalArgumentException e) {
            // 회원 정보 저장 성공 여부와 파일 정리 실패는 구분
            System.err.println("사용하지 않는 프로필 사진 정리가 필요합니다.");
        }
    }
}
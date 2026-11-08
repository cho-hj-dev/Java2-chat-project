package dao;

import db.DBConnection;
import domain.Member;
import domain.MemberProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import security.PasswordHasher;
import java.security.GeneralSecurityException;

public class UserDao {

    // 비밀번호 원문이 아닌, 해시 처리된 값을 전달받음.
    public int insert(String loginId, String passwordHash,
                      String nickname) throws SQLException {
        return insert(loginId, passwordHash, nickname, "", "", "");
    }

    public int insert(String loginId, String passwordHash, String nickname,
                      String postalCode, String address, String addressDetail)
            throws SQLException {
        return insert(loginId, passwordHash, nickname, postalCode, address, addressDetail, null);
    }

    public int insert(String loginId, String passwordHash, String nickname,
                      String postalCode, String address, String addressDetail,
                      String profileImagePath) throws SQLException {
        String sql = """
                INSERT INTO users
                    (login_id, password_hash, nickname, postal_code, address, address_detail, profile_image_path)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, loginId);
            statement.setString(2, passwordHash);
            statement.setString(3, nickname);
            statement.setString(4, postalCode.isEmpty() ? null : postalCode);
            statement.setString(5, address.isEmpty() ? null : address);
            statement.setString(6, addressDetail.isEmpty() ? null : addressDetail);
            statement.setString(7, profileImagePath);

            return statement.executeUpdate();
        }
    }
    
    public int insertVerified(
            Connection connection,
            String loginId, String passwordHash, String nickname,
            String postalCode, String address, String addressDetail,
            String profileImagePath, String email) throws SQLException {

        String sql = """
                INSERT INTO users
                    (login_id, password_hash, nickname,
                     postal_code, address, address_detail,
                     profile_image_path, email, email_verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, loginId);
            statement.setString(2, passwordHash);
            statement.setString(3, nickname);
            statement.setString(4, postalCode.isEmpty() ? null : postalCode);
            statement.setString(5, address.isEmpty() ? null : address);
            statement.setString(6, addressDetail.isEmpty() ? null : addressDetail);
            statement.setString(7, profileImagePath);
            statement.setString(8, email);
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
    
    //입력 비밀번호는 SQL에 넣지 않고, 조회한 해시와 Java 코드에서 비교. 
    public Optional<Member> authenticate(String loginId, char[] password)
            throws SQLException, GeneralSecurityException {
        String sql = """
                SELECT user_id, login_id, password_hash, nickname, created_at
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

                String storedHash = result.getString("password_hash");
                if (!PasswordHasher.verify(password, storedHash)) {
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
    
    // 로그인한 회원 번호로 프로필 정보 조회
    public Optional<MemberProfile> findProfileById(int memberId)
            throws SQLException {

        String sql = """
                SELECT login_id, nickname, postal_code,
                       address, address_detail, profile_image_path
                FROM users
                WHERE user_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                MemberProfile profile = new MemberProfile(
                        result.getString("login_id"),
                        result.getString("nickname"),
                        result.getString("postal_code"),
                        result.getString("address"),
                        result.getString("address_detail"),
                        result.getString("profile_image_path")
                );

                return Optional.of(profile);
            }
        }
    }
}

package dao;

import db.DBConnection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Optional;

public class ChatAttachmentDao {

    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    public record StoredAttachment(
            long attachmentId,
            long messageId,
            String fileName,
            String mimeType,
            int fileSizeBytes,
            String sha256Hex,
            byte[] fileData
    ) {
    }

    /**
     * 파일 메시지와 원본 데이터를 같은 트랜잭션에서 저장한다.
     * 일반 채팅방은 발신자가 DB 참여자인 경우에만 저장할 수 있다.
     */
    public long save(
            String roomName,
            int senderUserId,
            String fileName,
            String mimeType,
            byte[] fileData
    ) throws SQLException {
        validateFile(fileName, mimeType, fileData);

        String messageSql = """
                INSERT INTO chat_messages
                    (room_id, sender_user_id, message_type, content)
                SELECT r.room_id, ?, 'FILE', ?
                FROM chat_rooms r
                WHERE r.room_name = ?
                  AND (
                      r.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members member
                          WHERE member.room_id = r.room_id
                            AND member.user_id = ?
                      )
                  )
                """;

        String attachmentSql = """
                INSERT INTO chat_attachments
                    (message_id, original_name, mime_type,
                     file_size_bytes, sha256_hex, file_data)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                long messageId;

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                messageSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, senderUserId);
                    statement.setString(2, fileName);
                    statement.setString(3, roomName);
                    statement.setInt(4, senderUserId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "채팅방 또는 발신자의 참여 기록을 찾지 못했습니다.");
                    }

                    messageId = generatedId(statement);
                }

                long attachmentId;

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                attachmentSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, messageId);
                    statement.setString(2, fileName);
                    statement.setString(3, mimeType);
                    statement.setInt(4, fileData.length);
                    statement.setString(5, sha256(fileData));
                    statement.setBytes(6, fileData);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("파일 원본을 저장하지 못했습니다.");
                    }

                    attachmentId = generatedId(statement);
                }

                connection.commit();
                return attachmentId;

            } catch (SQLException | RuntimeException error) {
                // 메시지 또는 파일 중 하나라도 실패하면 둘 다 저장하지 않는다.
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }

    /**
     * 다운로드 요청자의 방 참여 여부를 확인한 뒤 파일을 읽는다.
     * 참여하지 않은 방의 파일 ID를 알아내도 내용을 받을 수 없다.
     */
    public Optional<StoredAttachment> findById(
            long attachmentId,
            int viewerUserId
    ) throws SQLException {
        String sql = """
                SELECT a.attachment_id, a.message_id,
                       a.original_name, a.mime_type,
                       a.file_size_bytes, a.sha256_hex, a.file_data
                FROM chat_attachments a
                JOIN chat_messages m ON m.message_id = a.message_id
                JOIN chat_rooms r ON r.room_id = m.room_id
                WHERE a.attachment_id = ?
                  AND (
                      r.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members member
                          WHERE member.room_id = r.room_id
                            AND member.user_id = ?
                      )
                  )
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, attachmentId);
            statement.setInt(2, viewerUserId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(new StoredAttachment(
                        result.getLong("attachment_id"),
                        result.getLong("message_id"),
                        result.getString("original_name"),
                        result.getString("mime_type"),
                        result.getInt("file_size_bytes"),
                        result.getString("sha256_hex"),
                        result.getBytes("file_data")
                ));
            }
        }
    }

    private long generatedId(PreparedStatement statement)
            throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("저장된 데이터의 ID를 받지 못했습니다.");
            }
            return keys.getLong(1);
        }
    }

    private void validateFile(
            String fileName, String mimeType, byte[] fileData) {
        if (fileName == null || fileName.isBlank()
                || fileName.length() > 255
                || fileName.equals(".") || fileName.equals("..")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("잘못된 파일 이름입니다.");
        }

        // 수신자가 저장할 때 경로로 해석될 수 있는 문자를 거부한다.
        for (char forbidden : "<>:\"/\\|?*".toCharArray()) {
            if (fileName.indexOf(forbidden) >= 0) {
                throw new IllegalArgumentException("잘못된 파일 이름입니다.");
            }
        }

        if (mimeType == null || mimeType.length() > 100
                || !mimeType.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")) {
            throw new IllegalArgumentException("잘못된 파일 형식입니다.");
        }

        if (fileData == null || fileData.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "파일은 최대 10MB까지 전송할 수 있습니다.");
        }
    }

    private String sha256(byte[] fileData) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(fileData);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256을 사용할 수 없습니다.", error);
        }
    }
}
package dao;

import db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.Optional;

public class ImageGalleryDao {

    // 한 번에 가져오는 목록 수를 제한한다.
    // 이미지 원본은 이 목록에서 읽지 않고, 사용자가 선택했을 때 따로 가져온다.
    public static final int MAX_IMAGES = 100;

    // 갤러리 목록 한 칸에 표시할 정보다.
    // 그림판 이미지는 attachmentId가 없고, 파일 전송 이미지는 attachmentId가 있다.
    public record GalleryImage(
            long messageId,
            Long attachmentId,
            String senderName,
            String imageSource,
            String imageName,
            String mimeType,
            LocalDateTime sentAt
    ) {
    }
    
    // 선택한 이미지의 실제 바이트다.
    // 그림판과 전송 이미지 파일을 동일한 형태로 서버에 돌려준다.
    public record GalleryImageData(
            long messageId,
            String mimeType,
            byte[] bytes
    ) {
    }

    public List<GalleryImage> findImages(
            String roomName, int viewerUserId
    ) throws SQLException {

        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("채팅방 이름이 없습니다.");
        }
        if (viewerUserId <= 0) {
            throw new IllegalArgumentException("조회할 사용자 ID가 올바르지 않습니다.");
        }

        String sql = """
                SELECT m.message_id,
                       a.attachment_id,
                       u.nickname AS sender_name,
                       CASE
                           WHEN m.message_type = 'IMAGE'
                               THEN '그림판'
                           ELSE '전송 이미지 파일'
                       END AS image_source,
                       CASE
                           WHEN m.message_type = 'IMAGE'
                               THEN CONCAT('drawing-', m.message_id, '.png')
                           ELSE a.original_name
                       END AS image_name,
                       CASE
                           WHEN m.message_type = 'IMAGE'
                               THEN 'image/png'
                           ELSE a.mime_type
                       END AS mime_type,
                       m.sent_at
                FROM chat_messages m
                JOIN chat_rooms r ON r.room_id = m.room_id
                JOIN users u ON u.user_id = m.sender_user_id
                LEFT JOIN chat_attachments a
                    ON a.message_id = m.message_id
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
                  AND (
                      m.message_type = 'IMAGE'
                      OR (
                          m.message_type = 'FILE'
                          AND a.mime_type IN (
                              'image/png', 'image/jpeg', 'image/gif'
                          )
                      )
                  )
                ORDER BY m.sent_at DESC, m.message_id DESC
                LIMIT ?
                """;

        List<GalleryImage> images = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, roomName);
            statement.setInt(2, viewerUserId);
            statement.setInt(3, MAX_IMAGES);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long storedAttachmentId =
                            result.getLong("attachment_id");

                    // 그림판 이미지는 첨부파일 행이 없으므로 NULL로 구분한다.
                    Long attachmentId = result.wasNull()
                            ? null
                            : storedAttachmentId;

                    images.add(new GalleryImage(
                            result.getLong("message_id"),
                            attachmentId,
                            result.getString("sender_name"),
                            result.getString("image_source"),
                            result.getString("image_name"),
                            result.getString("mime_type"),
                            result.getTimestamp("sent_at")
                                    .toLocalDateTime()
                    ));
                }
            }
        }

        return images;
    }
    
    public Optional<GalleryImageData> findImage(
            String roomName, long messageId, int viewerUserId
    ) throws SQLException {

        if (roomName == null || roomName.isBlank()
                || messageId <= 0 || viewerUserId <= 0) {
            throw new IllegalArgumentException(
                    "이미지 조회 요청이 올바르지 않습니다.");
        }

        String sql = """
                SELECT m.message_type,
                       m.content,
                       a.mime_type,
                       a.file_size_bytes,
                       a.file_data
                FROM chat_messages m
                JOIN chat_rooms r ON r.room_id = m.room_id
                LEFT JOIN chat_attachments a
                    ON a.message_id = m.message_id
                WHERE r.room_name = ?
                  AND m.message_id = ?
                  AND (
                      r.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members member
                          WHERE member.room_id = r.room_id
                            AND member.user_id = ?
                      )
                  )
                  AND (
                      m.message_type = 'IMAGE'
                      OR (
                          m.message_type = 'FILE'
                          AND a.mime_type IN (
                              'image/png', 'image/jpeg', 'image/gif'
                          )
                      )
                  )
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, roomName);
            statement.setLong(2, messageId);
            statement.setInt(3, viewerUserId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    // 이미지가 없거나 현재 사용자가 이 방의 참여자가 아니다.
                    return Optional.empty();
                }

                if ("IMAGE".equals(result.getString("message_type"))) {
                    // 그림판 그림은 chat_messages.content에 Base64 PNG로 저장돼 있다.
                    try {
                        byte[] png = Base64.getDecoder().decode(
                                result.getString("content"));

                        if (png.length == 0 || png.length > 256 * 1024) {
                            return Optional.empty();
                        }
                        return Optional.of(new GalleryImageData(
                                messageId, "image/png", png));

                    } catch (IllegalArgumentException exception) {
                        // 저장된 그림 데이터가 올바른 Base64가 아니다.
                        return Optional.empty();
                    }
                }

                // 전송한 이미지 파일은 chat_attachments.file_data에 저장돼 있다.
                byte[] bytes = result.getBytes("file_data");
                int expectedSize = result.getInt("file_size_bytes");

                if (bytes == null || bytes.length == 0
                        || bytes.length != expectedSize
                        || bytes.length > 10 * 1024 * 1024) {
                    return Optional.empty();
                }

                return Optional.of(new GalleryImageData(
                        messageId,
                        result.getString("mime_type"),
                        bytes));
            }
        }
    }
}
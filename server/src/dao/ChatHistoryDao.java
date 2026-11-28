package dao;

import db.DBConnection;
import dto.type.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatHistoryDao {

    public static final int MAX_RECENT_MESSAGES = 30;

    // 이전 대화 한 건을 화면에 전달할 때 필요한 정보다.
    // 답장이 아닌 메시지는 마지막 세 값이 null이다.
    public record StoredMessage(
            long messageId,
            int senderUserId,
            String senderLoginId,
            String senderName,
            Integer recipientUserId,
            MessageType type,
            String content,
            LocalDateTime sentAt,
            Long replyToMessageId,
            String originalSenderName,
            String originalPreview) {
    }

    public List<StoredMessage> findRecent(
            String roomName, int viewerUserId) throws SQLException {

        // 최신 30건을 가져온다.
        // 일반 방은 DB 참여자만 조회하고, 귓속말은 당사자에게만 보여준다.
        // 답장의 원문은 같은 방의 공개 메시지에서만 찾는다.
        String sql = """
                SELECT m.message_id,
                       m.sender_user_id,
                       u.login_id AS sender_login_id,
                       u.nickname AS sender_name,
                       m.recipient_user_id,
                       m.message_type,
                       CASE
                           WHEN m.message_type = 'FILE' THEN
                               COALESCE(
                                   CONCAT(
                                       a.attachment_id, '|',
                                       a.original_name, '|',
                                       a.file_size_bytes),
                                   '')
                           ELSE m.content
                       END AS content,
                       m.sent_at,
                       m.reply_to_message_id,
                       original_author.nickname AS original_sender_name,
                       CASE
                           WHEN original.message_type = 'CHAT'
                               THEN original.content
                           WHEN original.message_type = 'IMAGE'
                               THEN '[그림]'
                           WHEN original.message_type = 'EMOTICON'
                               THEN '[이모티콘]'
                           WHEN original.message_type = 'FILE'
                               THEN CONCAT(
                                   '[파일] ',
                                   COALESCE(
                                       original_file.original_name,
                                       '파일'))
                       END AS original_preview
                FROM chat_messages m
                JOIN chat_rooms r
                    ON r.room_id = m.room_id
                JOIN users u
                    ON u.user_id = m.sender_user_id
                LEFT JOIN chat_attachments a
                    ON a.message_id = m.message_id
                LEFT JOIN chat_messages original
                    ON original.message_id = m.reply_to_message_id
                   AND original.room_id = m.room_id
                   AND original.message_type IN
                       ('CHAT', 'IMAGE', 'EMOTICON', 'FILE')
                LEFT JOIN users original_author
                    ON original_author.user_id =
                       original.sender_user_id
                LEFT JOIN chat_attachments original_file
                    ON original_file.message_id =
                       original.message_id
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
                      m.message_type <> 'WHISPER'
                      OR m.sender_user_id = ?
                      OR m.recipient_user_id = ?
                  )
                ORDER BY m.sent_at DESC, m.message_id DESC
                LIMIT ?
                """;

        List<StoredMessage> messages = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, roomName);
            statement.setInt(2, viewerUserId);
            statement.setInt(3, viewerUserId);
            statement.setInt(4, viewerUserId);
            statement.setInt(5, MAX_RECENT_MESSAGES);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    // 귓속말이 아닌 메시지에는 수신자 ID가 없다.
                    int recipientId =
                            result.getInt("recipient_user_id");
                    Integer recipientUserId = result.wasNull()
                            ? null
                            : recipientId;

                    // 일반 메시지는 답장 대상 ID가 NULL이다.
                    long originalMessageId =
                            result.getLong("reply_to_message_id");
                    Long replyToMessageId = result.wasNull()
                            ? null
                            : originalMessageId;

                    messages.add(new StoredMessage(
                            result.getLong("message_id"),
                            result.getInt("sender_user_id"),
                            result.getString("sender_login_id"),
                            result.getString("sender_name"),
                            recipientUserId,
                            MessageType.valueOf(
                                    result.getString("message_type")),
                            result.getString("content"),
                            result.getTimestamp("sent_at")
                                    .toLocalDateTime(),
                            replyToMessageId,
                            result.getString("original_sender_name"),
                            result.getString("original_preview")));
                }
            }
        }

        // SQL은 최신 건부터 조회했다. 화면에는 오래된 순서로 표시한다.
        Collections.reverse(messages);
        return messages;
    }
}
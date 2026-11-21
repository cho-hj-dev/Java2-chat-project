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

    // 화면 표시와 응답 전송에 필요한 값만 담는다.
    public record StoredMessage(
            long messageId,
            int senderUserId,
            String senderLoginId,
            String senderName,
            Integer recipientUserId,
            MessageType type,
            String content,
            LocalDateTime sentAt) {
    }

    public List<StoredMessage> findRecent(
            String roomName, int viewerUserId) throws SQLException {

        // 방별로 최신 30건을 먼저 선택한다.
        // 일반 방은 DB 참여자만 조회할 수 있다.
        // 귓속말은 발신자 또는 수신자 본인에게만 보인다.
        String sql = """
                SELECT m.message_id,
                       m.sender_user_id,
                       u.login_id AS sender_login_id,
                       u.nickname AS sender_name,
                       m.recipient_user_id,
                       m.message_type,
                       m.content,
                       m.sent_at
                FROM chat_messages m
                JOIN chat_rooms r ON r.room_id = m.room_id
                JOIN users u ON u.user_id = m.sender_user_id
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
                    int recipientId =
                            result.getInt("recipient_user_id");

                    Integer recipientUserId = result.wasNull()
                            ? null
                            : recipientId;

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
                                    .toLocalDateTime()));
                }
            }
        }

        // SQL은 최신 건을 고르기 위해 내림차순으로 조회했다.
        // 채팅창에는 오래된 메시지부터 보여주도록 뒤집는다.
        Collections.reverse(messages);
        return messages;
    }
}
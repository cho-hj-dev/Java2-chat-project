package dao;

import db.DBConnection;
import dto.type.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChatSearchDao {

    // 한 번의 검색에서 가져올 최대 결과 수다.
    public static final int MAX_RESULTS = 50;

    // 검색 화면에 표시할 메시지 정보다.
    public record SearchResult(
            long messageId,
            String senderLoginId,
            String senderName,
            MessageType messageType,
            String content,
            LocalDateTime sentAt) {
    }

    public List<SearchResult> search(
            String roomName, int viewerUserId, String keyword)
            throws SQLException {

        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("채팅방 이름이 없습니다.");
        }
        if (viewerUserId <= 0) {
            throw new IllegalArgumentException("조회할 사용자 ID가 올바르지 않습니다.");
        }
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }

        // 검색어의 앞뒤 공백만 제거한다. 중간 공백은 검색어의 일부로 사용한다.
        String trimmedKeyword = keyword.trim();

        String sql = """
                SELECT m.message_id,
                       u.login_id AS sender_login_id,
                       u.nickname AS sender_name,
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
                  AND m.message_type IN ('CHAT', 'WHISPER')
                  AND (
                      m.message_type <> 'WHISPER'
                      OR m.sender_user_id = ?
                      OR m.recipient_user_id = ?
                  )
                  AND INSTR(m.content, ?) > 0
                ORDER BY m.sent_at DESC, m.message_id DESC
                LIMIT ?
                """;

        List<SearchResult> results = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, roomName);
            statement.setInt(2, viewerUserId);
            statement.setInt(3, viewerUserId);
            statement.setInt(4, viewerUserId);
            statement.setString(5, trimmedKeyword);
            statement.setInt(6, MAX_RESULTS);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    results.add(new SearchResult(
                            result.getLong("message_id"),
                            result.getString("sender_login_id"),
                            result.getString("sender_name"),
                            MessageType.valueOf(
                                    result.getString("message_type")),
                            result.getString("content"),
                            result.getTimestamp("sent_at")
                                    .toLocalDateTime()));
                }
            }
        }

        // 최신 메시지가 먼저 나오도록 SQL 조회 순서를 유지한다.
        return results;
    }
}
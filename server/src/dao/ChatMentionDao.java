package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

/**
 * 확인된 멘션 대상만 메시지와 연결한다.
 * 호출자가 메시지 저장과 같은 트랜잭션의 Connection을 전달한다.
 */
public class ChatMentionDao {

    public void insert(
            Connection connection,
            long messageId,
            String roomName,
            Set<Integer> mentionedUserIds) throws SQLException {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }

        // 일반 방에서는 DB 참여자만 기록한다. 로비에서는 호출자가
        // 현재 입장 중인 회원으로 대상을 제한한 후 이 메서드를 호출한다.
        String sql = """
                INSERT INTO chat_message_mentions
                    (message_id, mentioned_user_id)
                SELECT message.message_id, mentioned.user_id
                FROM chat_messages message
                JOIN chat_rooms room
                    ON room.room_id = message.room_id
                JOIN users mentioned
                    ON mentioned.user_id = ?
                WHERE message.message_id = ?
                  AND room.room_name = ?
                  AND message.message_type = 'CHAT'
                  AND (
                      room.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members member
                          WHERE member.room_id = room.room_id
                            AND member.user_id = mentioned.user_id
                      )
                  )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            for (int userId : mentionedUserIds) {
                statement.setInt(1, userId);
                statement.setLong(2, messageId);
                statement.setString(3, roomName);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "멘션 대상이 채팅방 참여자가 아닙니다.");
                }
            }
        }
    }
}

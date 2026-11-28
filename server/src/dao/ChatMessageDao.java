package dao;

import db.DBConnection;
import dto.type.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.Set;

public class ChatMessageDao {

    public long save(
            String roomName,
            int senderUserId,
            Integer recipientUserId,
            MessageType type,
            String content) throws SQLException {
        return save(roomName, senderUserId, recipientUserId,
                type, content, Set.of());
    }

    // 일반 채팅과 확인된 멘션 대상은 한 트랜잭션에서 함께 저장한다.
    public long save(
            String roomName,
            int senderUserId,
            Integer recipientUserId,
            MessageType type,
            String content,
            Set<Integer> mentionedUserIds) throws SQLException {

        if (type == MessageType.WHISPER
                && recipientUserId == null) {
            throw new IllegalArgumentException(
                    "귓속말 수신자가 없습니다.");
        }
        
        if (type != MessageType.CHAT
                && type != MessageType.IMAGE
                && type != MessageType.WHISPER
                && type != MessageType.EMOTICON) {
            throw new IllegalArgumentException(
                    "저장할 수 없는 메시지 종류입니다.");
        }

        // 방 이름을 DB의 room_id로 변환해 저장한다.
        // 일반 방은 발신자가 DB 참여자인지도 함께 검사한다.
        // Lobby는 별도 참여자 테이블 없이 로그인 상태로 관리한다.
        String sql = """
                INSERT INTO chat_messages
                    (room_id, sender_user_id, recipient_user_id,
                     message_type, content)
                SELECT r.room_id, ?, ?, ?, ?
                FROM chat_rooms r
                WHERE r.room_name = ?
                  AND (
                      r.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members m
                          WHERE m.room_id = r.room_id
                            AND m.user_id = ?
                      )
                  )
                """;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long messageId;
                try (PreparedStatement statement = connection.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS)) {

                    statement.setInt(1, senderUserId);
                    if (recipientUserId == null) {
                        statement.setNull(2, Types.INTEGER);
                    } else {
                        statement.setInt(2, recipientUserId);
                    }
                    statement.setString(3, type.name());
                    statement.setString(4, content);
                    statement.setString(5, roomName);
                    statement.setInt(6, senderUserId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "채팅방 또는 발신자의 참여 기록을 찾지 못했습니다.");
                    }
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "저장된 메시지 ID를 받지 못했습니다.");
                        }
                        messageId = keys.getLong(1);
                    }
                }

                new ChatMentionDao().insert(connection, messageId,
                        roomName, mentionedUserIds);
                connection.commit();
                return messageId;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}

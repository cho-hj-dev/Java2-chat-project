package dao;

import db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;

public class ChatReplyDao {

    // 입력창 위와 답장 메시지에 보여줄 원문 길이.
    private static final int PREVIEW_LENGTH = 80;

    public record ReplyTarget(
            long messageId,
            String senderName,
            String preview
    ) {
    }

    /**
     * 답장할 원문을 조회한다.
     * 없는 메시지, 다른 방의 메시지, 참여하지 않은 방의 메시지는
     * 모두 Optional.empty()로 처리해 내용을 노출하지 않는다.
     */
    public Optional<ReplyTarget> findTarget(
            String roomName, int viewerUserId, long targetMessageId
    ) throws SQLException {

        validateRequest(roomName, viewerUserId, targetMessageId);

        String sql = """
                SELECT target.message_id,
                       author.nickname AS sender_name,
                       CASE
                           WHEN target.message_type = 'CHAT'
                               THEN target.content
                           WHEN target.message_type = 'IMAGE'
                               THEN '[그림]'
                           WHEN target.message_type = 'EMOTICON'
                               THEN '[이모티콘]'
                           WHEN target.message_type = 'FILE'
                               THEN CONCAT('[파일] ',
                                   COALESCE(file.original_name, '파일'))
                       END AS original_preview
                FROM chat_messages target
                JOIN chat_rooms room
                    ON room.room_id = target.room_id
                JOIN users author
                    ON author.user_id = target.sender_user_id
                LEFT JOIN chat_attachments file
                    ON file.message_id = target.message_id
                WHERE room.room_name = ?
                  AND target.message_id = ?
                  AND target.message_type IN
                      ('CHAT', 'IMAGE', 'EMOTICON', 'FILE')
                  AND (
                      room.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members member
                          WHERE member.room_id = room.room_id
                            AND member.user_id = ?
                      )
                  )
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, roomName);
            statement.setLong(2, targetMessageId);
            statement.setInt(3, viewerUserId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(new ReplyTarget(
                        result.getLong("message_id"),
                        result.getString("sender_name"),
                        oneLine(result.getString("original_preview"))));
            }
        }
    }

    /**
     * 답장을 저장한다.
     * INSERT 시점에도 원문의 방과 발신자의 참여 여부를 다시 검사한다.
     * 화면에서 대상을 선택한 뒤 방을 나간 경우에도 저장되지 않는다.
     */
    public long saveReply(
            String roomName,
            int senderUserId,
            long targetMessageId,
            String replyText
    ) throws SQLException {
        return saveReply(roomName, senderUserId,
                targetMessageId, replyText, Set.of());
    }

    // 답장 내용과 멘션 대상은 함께 성공하거나 함께 취소한다.
    public long saveReply(
            String roomName,
            int senderUserId,
            long targetMessageId,
            String replyText,
            Set<Integer> mentionedUserIds
    ) throws SQLException {

        validateRequest(roomName, senderUserId, targetMessageId);

        if (replyText == null || replyText.isBlank()
                || replyText.length() > 2000) {
            throw new IllegalArgumentException(
                    "답장 내용은 1~2000자로 입력해 주세요.");
        }

        String sql = """
                INSERT INTO chat_messages
                    (room_id, sender_user_id, recipient_user_id,
                     reply_to_message_id, message_type, content)
                SELECT room.room_id, ?, NULL,
                       target.message_id, 'CHAT', ?
                FROM chat_rooms room
                JOIN chat_messages target
                    ON target.room_id = room.room_id
                   AND target.message_id = ?
                WHERE room.room_name = ?
                  AND target.message_type IN
                      ('CHAT', 'IMAGE', 'EMOTICON', 'FILE')
                  AND (
                      room.room_name = 'Lobby'
                      OR EXISTS (
                          SELECT 1
                          FROM chat_room_members member
                          WHERE member.room_id = room.room_id
                            AND member.user_id = ?
                      )
                  )
                """;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long messageId;
                try (PreparedStatement statement =
                        connection.prepareStatement(
                                sql, Statement.RETURN_GENERATED_KEYS)) {

                    statement.setInt(1, senderUserId);
                    statement.setString(2, replyText);
                    statement.setLong(3, targetMessageId);
                    statement.setString(4, roomName);
                    statement.setInt(5, senderUserId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "답장 대상이 없거나 이 방에 참여하지 않았습니다.");
                    }

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "저장된 답장 ID를 확인하지 못했습니다.");
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

    private static void validateRequest(
            String roomName, int userId, long messageId) {

        if (roomName == null || roomName.isBlank()
                || roomName.length() > 40
                || userId <= 0 || messageId <= 0) {
            throw new IllegalArgumentException(
                    "답장 대상 정보가 올바르지 않습니다.");
        }
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }

        // 원문에 줄바꿈이 있어도 답장 미리보기는 한 줄로 표시한다.
        String preview = text.replace('\r', ' ')
                .replace('\n', ' ')
                .strip();

        if (preview.length() > PREVIEW_LENGTH) {
            return preview.substring(0, PREVIEW_LENGTH) + "…";
        }
        return preview;
    }
}

package dao;

import db.DBConnection;
import domain.RoomType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatPersistenceDao {

    // DB에서 읽은 방의 기본 정보다.
    // 현재 접속 중인 사용자 목록과 DB의 참여자 목록은 별개로 관리한다.
    public record StoredRoom(long roomId, String name, RoomType type) {}

    public long createRoom(
            String name,
            RoomType type,
            int creatorUserId,
            Integer targetUserId) throws SQLException {

        String roomSql = """
                INSERT INTO chat_rooms (room_name, room_type, created_by)
                VALUES (?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                long roomId;

                try (PreparedStatement statement = connection.prepareStatement(
                        roomSql, Statement.RETURN_GENERATED_KEYS)) {

                    statement.setString(1, name);
                    statement.setString(2, type.name());
                    statement.setInt(3, creatorUserId);
                    statement.executeUpdate();

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("생성된 채팅방 ID를 확인할 수 없습니다.");
                        }
                        roomId = keys.getLong(1);
                    }
                }

                // 생성자는 항상 방의 참여자로 등록한다.
                insertMember(connection, roomId, creatorUserId);

                // 1:1 채팅이면 상대도 같은 트랜잭션에서 등록한다.
                if (type == RoomType.DIRECT) {
                    if (targetUserId == null || targetUserId == creatorUserId) {
                        throw new SQLException("1:1 채팅 상대가 올바르지 않습니다.");
                    }
                    insertMember(connection, roomId, targetUserId);
                }

                connection.commit();
                return roomId;

            } catch (SQLException exception) {
                // 방만 생기고 참여자 등록은 실패하는 상황을 막는다.
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<StoredRoom> findRooms() throws SQLException {
        String sql = """
                SELECT room_id, room_name, room_type
                FROM chat_rooms
                WHERE room_name <> 'Lobby'
                ORDER BY room_id
                """;

        List<StoredRoom> rooms = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                rooms.add(new StoredRoom(
                        result.getLong("room_id"),
                        result.getString("room_name"),
                        RoomType.valueOf(result.getString("room_type"))));
            }
        }

        return rooms;
    }

    public Set<Integer> findMemberIds(long roomId) throws SQLException {
        String sql = """
                SELECT user_id
                FROM chat_room_members
                WHERE room_id = ?
                """;

        Set<Integer> memberIds = new HashSet<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, roomId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    memberIds.add(result.getInt("user_id"));
                }
            }
        }

        return memberIds;
    }

    public void addMember(long roomId, int userId) throws SQLException {
        // 이미 참여 중이라면 중복 행을 만들지 않는다.
        String sql = """
                INSERT INTO chat_room_members (room_id, user_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, roomId);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    public boolean leaveRoom(long roomId, int userId) throws SQLException {
        String removeMemberSql = """
                DELETE FROM chat_room_members
                WHERE room_id = ? AND user_id = ?
                """;

        String removeEmptyRoomSql = """
                DELETE FROM chat_rooms
                WHERE room_id = ?
                  AND room_name <> 'Lobby'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM chat_room_members
                      WHERE room_id = ?
                  )
                """;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int removed;

                try (PreparedStatement statement =
                             connection.prepareStatement(removeMemberSql)) {
                    statement.setLong(1, roomId);
                    statement.setInt(2, userId);
                    removed = statement.executeUpdate();
                }

                if (removed == 0) {
                    connection.rollback();
                    return false;
                }

                // 마지막 참여자가 나갔다면 빈 방을 삭제한다.
                // chat_messages는 방 FK의 ON DELETE CASCADE로 함께 정리된다.
                try (PreparedStatement statement =
                             connection.prepareStatement(removeEmptyRoomSql)) {
                    statement.setLong(1, roomId);
                    statement.setLong(2, roomId);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void insertMember(
            Connection connection, long roomId, int userId)
            throws SQLException {

        String sql = """
                INSERT INTO chat_room_members (room_id, user_id)
                VALUES (?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roomId);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }
}
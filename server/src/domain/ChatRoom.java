package domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatRoom {

    // DB의 chat_rooms.room_id. 기존 로비 객체는 연결 전까지 0을 사용한다.
    private final long roomId;
    private final String name;
    private final RoomType type;

    // 방 화면에 연결된 사용자들이다. 접속이 끊기면 여기서는 제거한다.
    private final List<User> users = new ArrayList<>();

    // DB에 가입된 회원 ID들이다. 접속이 끊겨도 유지한다.
    private final Set<Integer> memberIds = new HashSet<>();

    // 기존 로비 생성 코드와의 호환을 위해 유지한다.
    public ChatRoom(String name) {
        this(0, name, RoomType.GROUP);
    }

    public ChatRoom(String name, RoomType type) {
        this(0, name, type);
    }

    public ChatRoom(long roomId, String name, RoomType type) {
        this.roomId = roomId;
        this.name = name;
        this.type = type;
    }

    public void addUser(User user) {
        if (users.contains(user)) {
            return;
        }

        // 실제 연결 사용자도 1:1 방에서는 두 명을 넘지 않는다.
        if (type == RoomType.DIRECT && users.size() >= 2) {
            throw new IllegalStateException(
                    "1:1 채팅방은 두 명까지만 참여할 수 있습니다.");
        }

        users.add(user);
    }

    public void removeUser(User user) {
        users.remove(user);
    }

    public void addMemberId(int userId) {
        memberIds.add(userId);
    }

    public void removeMemberId(int userId) {
        memberIds.remove(userId);
    }

    public boolean hasMember(int userId) {
        return memberIds.contains(userId);
    }

    public int getMemberCount() {
        return memberIds.size();
    }

    public long getRoomId() {
        return roomId;
    }

    public String getName() {
        return name;
    }

    public RoomType getType() {
        return type;
    }

    public List<User> getUsers() {
        return users;
    }

    // 기존 호출부에서 사용하는 메서드 이름은 유지한다.
    public boolean ieExistUser() {
        return !users.isEmpty();
    }
}
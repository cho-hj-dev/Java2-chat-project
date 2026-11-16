package domain;

import java.util.ArrayList;
import java.util.List;

public class ChatRoom {

    private final String name;
    private final RoomType type;
    private final List<User> users = new ArrayList<>();

    // 기존 코드에서 new ChatRoom("Lobby")를 계속 사용할 수 있게 유지한다.
    public ChatRoom(String name) {
        this(name, RoomType.GROUP);
    }

    public ChatRoom(String name, RoomType type) {
        this.name = name;
        this.type = type;
    }

    public void addUser(User user) {
        // 같은 사용자가 입장 요청을 여러 번 보내도 중복 등록하지 않는다.
        if (users.contains(user)) {
            return;
        }

        // 1:1 방에는 두 명까지만 참여할 수 있다.
        if (type == RoomType.DIRECT && users.size() >= 2) {
            throw new IllegalStateException("1:1 채팅방은 두 명까지만 참여할 수 있습니다.");
        }

        users.add(user);
    }

    public void removeUser(User user) {
        users.remove(user);
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

    // 기존 ChatService에서 호출하는 메서드 이름을 유지한다.
    public boolean ieExistUser() {
        return !users.isEmpty();
    }
}
package domain;

public class ChatRoom {

    private final String name;
    private final RoomType type;

    // 기존 방 목록을 읽는 코드와 호환되도록 기본값은 GROUP으로 둔다.
    public ChatRoom(String name) {
        this(name, RoomType.GROUP);
    }

    public ChatRoom(String name, RoomType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public RoomType getType() {
        return type;
    }
}
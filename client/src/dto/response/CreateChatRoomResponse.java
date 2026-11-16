package dto.response;

import domain.RoomType;

public class CreateChatRoomResponse {
    private final String name;
    private final RoomType type;

    public CreateChatRoomResponse(String message) {
        // 서버 형식: 방이름,방종류
        String[] values = message.split(",", 2);
        if (values.length != 2) {
            throw new IllegalArgumentException("잘못된 방 생성 알림");
        }

        name = values[0];
        type = RoomType.valueOf(values[1]);
    }

    public String getName() {
        return name;
    }

    public RoomType getType() {
        return type;
    }
}
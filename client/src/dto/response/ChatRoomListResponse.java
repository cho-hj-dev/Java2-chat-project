package dto.response;

import domain.ChatRoom;
import domain.RoomType;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomListResponse {
    private final List<ChatRoom> chatRooms = new ArrayList<>();

    public ChatRoomListResponse(String message) {
        if ("empty".equals(message)) {
            return;
        }

        // 서버 형식: 방이름,방종류|방이름,방종류
        for (String roomValue : message.split("\\|")) {
            String[] values = roomValue.split(",", 2);
            if (values.length != 2) {
                throw new IllegalArgumentException("잘못된 방 목록");
            }

            chatRooms.add(new ChatRoom(
                    values[0],
                    RoomType.valueOf(values[1])));
        }
    }

    public List<ChatRoom> getChatRooms() {
        return chatRooms;
    }
}
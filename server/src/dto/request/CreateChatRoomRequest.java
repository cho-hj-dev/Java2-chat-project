package dto.request;

import domain.RoomType;

public class CreateChatRoomRequest {

    private final String name;
    private final RoomType roomType;
    private final String userId;
    private final String targetUserId;

    public CreateChatRoomRequest(String message) {
        String[] values = message.split(",", -1);

        if (values.length == 2) {
            // 기존 "방이름,생성자ID" 요청도 당분간 처리한다.
            name = values[0];
            roomType = RoomType.GROUP;
            userId = values[1];
            targetUserId = "";
        } else if (values.length == 4) {
            // 새 요청: 방이름,방종류,생성자ID,상대ID
            name = values[0];
            roomType = RoomType.valueOf(values[1]);
            userId = values[2];
            targetUserId = values[3];
        } else {
            throw new IllegalArgumentException("잘못된 채팅방 생성 요청");
        }
    }

    public String getName() {
        return name;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public String getUserId() {
        return userId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }
}
package dto.request;

import domain.RoomType;
import dto.type.DtoType;

public class CreateChatRoomRequest extends DTO {

    private final String chatRoomName;
    private final RoomType roomType;
    private final String userId;
    private final String targetUserId;

    public CreateChatRoomRequest(
            String chatRoomName,
            RoomType roomType,
            String userId,
            String targetUserId) {

        super(DtoType.CREATE_CHAT);
        this.chatRoomName = chatRoomName;
        this.roomType = roomType;
        this.userId = userId;
        this.targetUserId = targetUserId;
    }

    @Override
    public String toString() {
        // 서버 CreateChatRoomRequest가 네 값을 같은 순서로 해석한다.
        // 그룹 방은 상대가 없으므로 마지막 값이 빈 문자열이다.
        return super.toString()
                + chatRoomName + ","
                + roomType + ","
                + userId + ","
                + targetUserId;
    }
}
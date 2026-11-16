package dto.response;

import domain.ChatRoom;
import dto.type.DtoType;

public class CreateChatRoomResponse extends DTO {
    private final ChatRoom chatRoom;

    public CreateChatRoomResponse(ChatRoom chatRoom) {
        super(DtoType.CREATE_CHAT);
        this.chatRoom = chatRoom;
    }

    @Override
    public String toString() {
        // 형식: CREATE_CHAT:방이름,방종류
        return super.toString()
                + chatRoom.getName() + "," + chatRoom.getType();
    }
}
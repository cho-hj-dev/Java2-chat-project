package dto.response;

import domain.ChatRoom;
import dto.type.DtoType;

import java.util.List;

public class ChatRoomListResponse extends DTO {

    List<ChatRoom> chatRooms;

    public ChatRoomListResponse(List<ChatRoom> chatRooms) {
        super(DtoType.CHAT_ROOM_LIST);

        this.chatRooms = chatRooms;
    }

    @Override
    public String toString() {
        String str = super.toString();

        // 각 방을 '방이름,방종류'로 묶고 |로 구분한다.
        for (ChatRoom room : chatRooms) {
            str += room.getName() + "," + room.getType() + "|";
        }

        return chatRooms.isEmpty()
                ? str + "empty"
                : str.substring(0, str.length() - 1);
    }
}

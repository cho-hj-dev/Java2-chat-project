package dto.request;

import dto.type.DtoType;

public class InviteChatRequest extends DTO {
    private final String roomName;
    private final String targetId;

    public InviteChatRequest(String roomName, String targetId) {
        super(DtoType.INVITE_CHAT);
        this.roomName = roomName;
        this.targetId = targetId;
    }

    @Override
    public String toString() {
        // 서버가 기대하는 형식: INVITE_CHAT:방이름,대상ID
        return super.toString() + roomName + "," + targetId;
    }
}
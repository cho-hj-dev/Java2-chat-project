package dto.response;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ChatSearchEndResponse extends DTO {

    private final String roomName;
    private final boolean success;
    private final int resultCount;

    public ChatSearchEndResponse(
            String roomName, boolean success, int resultCount) {
        super(DtoType.CHAT_SEARCH_END);
        this.roomName = roomName;
        this.success = success;
        this.resultCount = resultCount;
    }

    @Override
    public String toString() {
        // 완료 형식: Base64(방 이름),OK 또는 ERROR,검색 결과 수
        String encodedRoomName = Base64.getEncoder().encodeToString(
                roomName.getBytes(StandardCharsets.UTF_8));

        return super.toString()
                + encodedRoomName + ","
                + (success ? "OK" : "ERROR") + ","
                + resultCount;
    }
}
package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ChatSearchRequest extends DTO {

    private final String roomName;
    private final String keyword;

    public ChatSearchRequest(String roomName, String keyword) {
        super(DtoType.CHAT_SEARCH);

        if (roomName == null || roomName.isBlank()
                || roomName.length() > 40) {
            throw new IllegalArgumentException("채팅방 이름이 올바르지 않습니다.");
        }
        if (keyword == null || keyword.isBlank()
                || keyword.trim().length() > 100) {
            throw new IllegalArgumentException("검색어를 확인해 주세요.");
        }

        this.roomName = roomName;
        this.keyword = keyword.trim();
    }

    @Override
    public String toString() {
        // 서버 요청 형식: CHAT_SEARCH:Base64(방 이름),Base64(검색어)
        return super.toString()
                + encode(roomName) + ","
                + encode(keyword);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
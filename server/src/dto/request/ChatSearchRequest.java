package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ChatSearchRequest {

    private final String roomName;
    private final String keyword;

    public ChatSearchRequest(String message) {
        // 요청 형식: Base64(방 이름),Base64(검색어)
        // 쉼표가 들어간 방 이름이나 검색어도 안전하게 전달하기 위해 인코딩한다.
        String[] fields = message.split(",", -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException("검색 요청 형식이 올바르지 않습니다.");
        }

        try {
            roomName = decode(fields[0]);
            keyword = decode(fields[1]).trim();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "검색 요청의 인코딩이 올바르지 않습니다.", exception);
        }

        if (roomName.isBlank() || roomName.length() > 40
                || keyword.isBlank() || keyword.length() > 100) {
            throw new IllegalArgumentException(
                    "채팅방 이름 또는 검색어가 올바르지 않습니다.");
        }
    }

    public String getRoomName() {
        return roomName;
    }

    public String getKeyword() {
        return keyword;
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
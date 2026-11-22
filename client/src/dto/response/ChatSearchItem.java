package dto.response;

import dto.type.MessageType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public record ChatSearchItem(
        String roomName,
        long messageId,
        String senderLoginId,
        String senderName,
        MessageType messageType,
        LocalDateTime sentAt,
        String content) {

    public static ChatSearchItem parse(String response) {
        // 서버 응답: 방 이름, 메시지 ID, 발신 ID, 닉네임,
        // 메시지 종류, 전송 시각, 내용
        String[] values = response.split(",", -1);
        if (values.length != 7) {
            throw new IllegalArgumentException("잘못된 검색 결과 응답입니다.");
        }

        return new ChatSearchItem(
                decode(values[0]),
                Long.parseLong(values[1]),
                decode(values[2]),
                decode(values[3]),
                MessageType.valueOf(values[4]),
                LocalDateTime.parse(values[5]),
                decode(values[6]));
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
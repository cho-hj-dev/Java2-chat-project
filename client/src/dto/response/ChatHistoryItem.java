package dto.response;

import dto.type.MessageType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public record ChatHistoryItem(
        String roomName,
        long messageId,
        String senderLoginId,
        String senderName,
        Integer recipientUserId,
        MessageType messageType,
        LocalDateTime sentAt,
        String content) {

    public static ChatHistoryItem parse(String response) {
        // 서버가 보낸 항목: 방,ID,발신ID,닉네임,수신ID,종류,시각,내용
        String[] values = response.split(",", -1);
        if (values.length != 8) {
            throw new IllegalArgumentException("잘못된 이전 대화 응답입니다.");
        }

        Integer recipientId = values[4].isEmpty()
                ? null
                : Integer.valueOf(values[4]);

        return new ChatHistoryItem(
                decode(values[0]),
                Long.parseLong(values[1]),
                decode(values[2]),
                decode(values[3]),
                recipientId,
                MessageType.valueOf(values[5]),
                LocalDateTime.parse(values[6]),
                decode(values[7]));
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
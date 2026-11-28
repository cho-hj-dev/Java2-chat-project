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
        String content,
        Long replyToMessageId,
        String originalSenderName,
        String originalPreview) {

    public static ChatHistoryItem parse(String response) {
    	// 방, ID, 발신 ID, 닉네임, 수신 ID, 종류, 시각, 내용,
    	// 원문 ID, 원문 작성자, 원문 미리보기
    	String[] values = response.split(",", -1);
    	if (values.length != 11) {
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
                decode(values[7]),
                values[8].isEmpty() ? null : Long.valueOf(values[8]),
                decode(values[9]),
                decode(values[10]));
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
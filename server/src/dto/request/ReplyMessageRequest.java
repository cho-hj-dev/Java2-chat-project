package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ReplyMessageRequest {

    private final String roomName;
    private final long targetMessageId;
    private final String replyText;

    public ReplyMessageRequest(String message) {
        // 요청 본문: Base64(방 이름),원문 메시지 ID,Base64(답장 내용)
        String[] fields = message.split(",", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException(
                    "답장 요청 형식이 올바르지 않습니다.");
        }

        try {
            roomName = decode(fields[0]);
            targetMessageId = Long.parseLong(fields[1]);
            replyText = decode(fields[2]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "답장 요청 값이 올바르지 않습니다.", exception);
        }

        if (roomName.isBlank() || roomName.length() > 40
                || targetMessageId <= 0
                || replyText.isBlank()
                || replyText.length() > 2000) {
            throw new IllegalArgumentException(
                    "답장 대상 또는 내용이 올바르지 않습니다.");
        }
    }

    public String getRoomName() {
        return roomName;
    }

    public long getTargetMessageId() {
        return targetMessageId;
    }

    public String getReplyText() {
        return replyText;
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
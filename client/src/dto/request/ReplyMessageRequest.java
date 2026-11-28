package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ReplyMessageRequest extends DTO {

    private final String roomName;
    private final long targetMessageId;
    private final String replyText;

    public ReplyMessageRequest(
            String roomName, long targetMessageId, String replyText) {
        super(DtoType.REPLY_MESSAGE);

        if (roomName == null || roomName.isBlank()
                || roomName.length() > 40
                || targetMessageId <= 0
                || replyText == null || replyText.isBlank()
                || replyText.length() > 2000) {
            throw new IllegalArgumentException(
                    "답장 대상 또는 내용이 올바르지 않습니다.");
        }

        this.roomName = roomName;
        this.targetMessageId = targetMessageId;
        this.replyText = replyText;
    }

    @Override
    public String toString() {
        return super.toString()
                + encode(roomName) + ","
                + targetMessageId + ","
                + encode(replyText);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
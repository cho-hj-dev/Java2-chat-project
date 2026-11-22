package dto.response;

import dto.type.DtoType;
import dto.type.MessageType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public class ChatSearchItemResponse extends DTO {

    private final String roomName;
    private final long messageId;
    private final String senderLoginId;
    private final String senderName;
    private final MessageType messageType;
    private final String content;
    private final LocalDateTime sentAt;

    public ChatSearchItemResponse(
            String roomName,
            long messageId,
            String senderLoginId,
            String senderName,
            MessageType messageType,
            String content,
            LocalDateTime sentAt) {

        super(DtoType.CHAT_SEARCH_ITEM);
        this.roomName = roomName;
        this.messageId = messageId;
        this.senderLoginId = senderLoginId;
        this.senderName = senderName;
        this.messageType = messageType;
        this.content = content;
        this.sentAt = sentAt;
    }

    @Override
    public String toString() {
        // 검색 결과 한 건을 한 줄로 전송한다.
        // 문자열은 Base64로 인코딩해 쉼표·줄바꿈과 충돌하지 않게 한다.
        return super.toString()
                + encode(roomName) + ","
                + messageId + ","
                + encode(senderLoginId) + ","
                + encode(senderName) + ","
                + messageType.name() + ","
                + sentAt + ","
                + encode(content);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
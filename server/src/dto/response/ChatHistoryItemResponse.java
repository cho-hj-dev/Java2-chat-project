package dto.response;

import dto.type.DtoType;
import dto.type.MessageType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public class ChatHistoryItemResponse extends DTO {

    private final String roomName;
    private final long messageId;
    private final String senderLoginId;
    private final String senderName;
    private final Integer recipientUserId;
    private final MessageType messageType;
    private final String content;
    private final LocalDateTime sentAt;

    public ChatHistoryItemResponse(
            String roomName,
            long messageId,
            String senderLoginId,
            String senderName,
            Integer recipientUserId,
            MessageType messageType,
            String content,
            LocalDateTime sentAt) {

        super(DtoType.CHAT_HISTORY_ITEM);
        this.roomName = roomName;
        this.messageId = messageId;
        this.senderLoginId = senderLoginId;
        this.senderName = senderName;
        this.recipientUserId = recipientUserId;
        this.messageType = messageType;
        this.content = content;
        this.sentAt = sentAt;
    }

    @Override
    public String toString() {
        // 형식:
        // 방이름,메시지ID,발신자ID,발신자닉네임,귓속말수신자ID,
        // 메시지종류,전송시각,내용
        // 문자열은 Base64로 인코딩해 쉼표·줄바꿈 충돌을 막는다.
        return super.toString()
                + encode(roomName) + ","
                + messageId + ","
                + encode(senderLoginId) + ","
                + encode(senderName) + ","
                + (recipientUserId == null ? "" : recipientUserId) + ","
                + messageType.name() + ","
                + sentAt + ","
                + encode(content);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
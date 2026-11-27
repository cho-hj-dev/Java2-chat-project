package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GalleryGetRequest extends DTO {

    private final String roomName;
    private final long messageId;

    public GalleryGetRequest(String roomName, long messageId) {
        super(DtoType.GALLERY_GET);

        if (roomName == null || roomName.isBlank()
                || roomName.length() > 40 || messageId <= 0) {
            throw new IllegalArgumentException(
                    "이미지 요청 정보가 올바르지 않습니다.");
        }

        this.roomName = roomName;
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        // GALLERY_GET:Base64(방 이름),메시지 ID
        return super.toString()
                + Base64.getEncoder().encodeToString(
                        roomName.getBytes(StandardCharsets.UTF_8))
                + "," + messageId;
    }
}
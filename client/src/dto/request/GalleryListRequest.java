package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GalleryListRequest extends DTO {

    private final String roomName;

    public GalleryListRequest(String roomName) {
        super(DtoType.GALLERY_LIST);

        if (roomName == null || roomName.isBlank()
                || roomName.length() > 40) {
            throw new IllegalArgumentException(
                    "채팅방 이름이 올바르지 않습니다.");
        }

        this.roomName = roomName;
    }

    @Override
    public String toString() {
        // GALLERY_LIST:Base64(채팅방 이름)
        // 사용자 ID는 보내지 않는다. 서버가 로그인한 소켓에서 확인한다.
        return super.toString()
                + Base64.getEncoder().encodeToString(
                        roomName.getBytes(StandardCharsets.UTF_8));
    }
}
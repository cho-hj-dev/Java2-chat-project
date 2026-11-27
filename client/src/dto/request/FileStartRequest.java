package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class FileStartRequest extends DTO {

    private final String roomName;
    private final String fileName;
    private final String mimeType;
    private final int fileSizeBytes;
    private final String sha256Hex;

    public FileStartRequest(
            String roomName,
            String fileName,
            String mimeType,
            int fileSizeBytes,
            String sha256Hex) {
        super(DtoType.FILE_START);

        if (roomName == null || roomName.isBlank()
                || fileName == null || fileName.isBlank()
                || mimeType == null || mimeType.isBlank()
                || fileSizeBytes < 0
                || fileSizeBytes > 10 * 1024 * 1024
                || sha256Hex == null
                || !sha256Hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("파일 정보가 올바르지 않습니다.");
        }

        this.roomName = roomName;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Hex = sha256Hex;
    }

    @Override
    public String toString() {
        // 방 이름과 파일 이름은 한글·쉼표가 포함될 수 있으므로 인코딩한다.
        return super.toString()
                + encode(roomName) + ","
                + encode(fileName) + ","
                + mimeType + ","
                + fileSizeBytes + ","
                + sha256Hex;
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
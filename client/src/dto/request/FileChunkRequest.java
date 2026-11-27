package dto.request;

import dto.type.DtoType;

import java.util.Arrays;
import java.util.Base64;

public class FileChunkRequest extends DTO {

    public static final int CHUNK_BYTES = 16 * 1024;

    private final int sequence;
    private final byte[] bytes;

    public FileChunkRequest(int sequence, byte[] bytes) {
        super(DtoType.FILE_CHUNK);

        if (sequence < 0 || bytes == null
                || bytes.length == 0
                || bytes.length > CHUNK_BYTES) {
            throw new IllegalArgumentException("파일 조각이 올바르지 않습니다.");
        }

        this.sequence = sequence;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public String toString() {
        // 조각 번호로 누락·중복 여부를 서버에서 검사한다.
        return super.toString()
                + sequence + ","
                + Base64.getEncoder().encodeToString(bytes);
    }
}
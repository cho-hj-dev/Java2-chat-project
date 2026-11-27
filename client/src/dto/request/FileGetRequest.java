package dto.request;

import dto.type.DtoType;

public class FileGetRequest extends DTO {

    private final long attachmentId;

    public FileGetRequest(long attachmentId) {
        super(DtoType.FILE_GET);
        if (attachmentId <= 0) {
            throw new IllegalArgumentException("파일 ID가 올바르지 않습니다.");
        }
        this.attachmentId = attachmentId;
    }

    @Override
    public String toString() {
        return super.toString() + attachmentId;
    }
}
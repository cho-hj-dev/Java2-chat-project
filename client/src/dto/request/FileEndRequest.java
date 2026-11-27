package dto.request;

import dto.type.DtoType;

public class FileEndRequest extends DTO {

    public FileEndRequest() {
        super(DtoType.FILE_END);
    }

    @Override
    public String toString() {
        // 본문이 없어도 "FILE_END:" 형태로 보내야 서버가 읽을 수 있다.
        return super.toString();
    }
}
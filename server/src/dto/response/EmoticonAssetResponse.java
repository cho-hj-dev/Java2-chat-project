package dto.response;

import dao.EmoticonAssetDao.EmoticonAsset;
import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EmoticonAssetResponse extends DTO {

    private final EmoticonAsset asset;

    public EmoticonAssetResponse(EmoticonAsset asset) {
        super(DtoType.EMOTICON_ASSET);
        this.asset = asset;
    }

    @Override
    public String toString() {
        // 한 줄씩 주고받는 기존 통신 방식에 맞춘다.
        // 표시 이름과 이미지 바이트는 Base64로 인코딩해
        // 쉼표·줄바꿈 때문에 응답이 잘리지 않도록 한다.
        String encodedName = Base64.getEncoder().encodeToString(
                asset.displayName().getBytes(StandardCharsets.UTF_8));
        String encodedImage = Base64.getEncoder().encodeToString(
                asset.imageData());

        // 형식: EMOTICON_ASSET:ID,Base64(이름),MIME,Base64(이미지)
        return super.toString()
                + asset.id() + ","
                + encodedName + ","
                + asset.mimeType() + ","
                + encodedImage;
    }
}
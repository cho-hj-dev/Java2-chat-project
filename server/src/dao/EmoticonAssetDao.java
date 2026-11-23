package dao;

import db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class EmoticonAssetDao {

    // 이모티콘은 채팅창에 표시하는 작은 이미지이므로 크기를 제한한다.
    private static final int MAX_IMAGE_BYTES = 1_048_576;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    // DB에서 읽은 한 개의 이모티콘 이미지와 표시 정보를 묶는다.
    public record EmoticonAsset(
            String id,
            String displayName,
            String mimeType,
            byte[] imageData
    ) {
    }

    /**
     * PNG/GIF 이미지를 DB에 등록한다.
     * 기존 메시지와 연결할 수 있도록 SMILE 같은 기존 ID를 사용한다.
     */
    public void insert(
            String id,
            String displayName,
            String mimeType,
            byte[] imageData
    ) throws SQLException {
        validate(id, displayName, mimeType, imageData);

        String sql = """
                INSERT INTO emoticon_assets
                    (emoticon_id, display_name, mime_type, image_data)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, displayName);
            statement.setString(3, mimeType);
            statement.setBytes(4, imageData);
            statement.executeUpdate();
        }
    }

    /** 채팅 기록의 이모티콘 ID에 해당하는 이미지를 찾는다. */
    public Optional<EmoticonAsset> findById(String id) throws SQLException {
        String sql = """
                SELECT emoticon_id, display_name, mime_type, image_data
                FROM emoticon_assets
                WHERE emoticon_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setString(1, id);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(readAsset(result));
                }
                return Optional.empty();
            }
        }
    }

    /** 클라이언트의 이모티콘 선택 목록에 사용할 전체 이미지를 읽는다. */
    public List<EmoticonAsset> findAll() throws SQLException {
        String sql = """
                SELECT emoticon_id, display_name, mime_type, image_data
                FROM emoticon_assets
                ORDER BY emoticon_id
                """;

        List<EmoticonAsset> assets = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                assets.add(readAsset(result));
            }
        }

        return assets;
    }

    private EmoticonAsset readAsset(ResultSet result) throws SQLException {
        return new EmoticonAsset(
                result.getString("emoticon_id"),
                result.getString("display_name"),
                result.getString("mime_type"),
                result.getBytes("image_data")
        );
    }

    private void validate(
            String id,
            String displayName,
            String mimeType,
            byte[] imageData
    ) {
        if (id == null || !id.matches("[A-Z0-9_]{1,32}")) {
            throw new IllegalArgumentException(
                    "이모티콘 ID는 영문 대문자, 숫자, _만 사용할 수 있습니다.");
        }

        if (displayName == null
                || displayName.isBlank()
                || displayName.length() > 50) {
            throw new IllegalArgumentException(
                    "이모티콘 이름은 1~50자로 입력해 주세요.");
        }

        if (imageData == null
                || imageData.length == 0
                || imageData.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(
                    "이모티콘 이미지는 1MB 이하여야 합니다.");
        }

        if ("image/png".equals(mimeType)) {
            // 확장자만 바꾼 파일을 PNG로 등록하지 않도록 파일 시작 부분을 확인한다.
            if (!startsWith(imageData, PNG_SIGNATURE)) {
                throw new IllegalArgumentException(
                        "올바른 PNG 파일이 아닙니다.");
            }
        } else if ("image/gif".equals(mimeType)) {
            // GIF 파일은 GIF87a 또는 GIF89a로 시작한다.
            boolean gifHeader =
                    startsWith(imageData, "GIF87a".getBytes(
                            java.nio.charset.StandardCharsets.US_ASCII))
                    || startsWith(imageData, "GIF89a".getBytes(
                            java.nio.charset.StandardCharsets.US_ASCII));

            if (!gifHeader) {
                throw new IllegalArgumentException(
                        "올바른 GIF 파일이 아닙니다.");
            }
        } else {
            throw new IllegalArgumentException(
                    "PNG 또는 GIF 이미지만 등록할 수 있습니다.");
        }
    }

    private boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length
                && Arrays.equals(
                        data, 0, signature.length,
                        signature, 0, signature.length);
    }
}
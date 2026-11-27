package service;

import dao.ChatAttachmentDao;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

public class FileUploadSession {

    private static final int CHUNK_BYTES = 16 * 1024;

    private final String roomName;
    private final String fileName;
    private final String mimeType;
    private final int expectedSize;
    private final String expectedSha256;

    private final ByteArrayOutputStream data;
    private int nextSequence;

    private FileUploadSession(
            String roomName, String fileName, String mimeType,
            int expectedSize, String expectedSha256) {
        this.roomName = roomName;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.expectedSize = expectedSize;
        this.expectedSha256 = expectedSha256;
        this.data = new ByteArrayOutputStream(
                Math.min(expectedSize, 64 * 1024));
    }

    public static FileUploadSession fromStartMessage(String message) {
        // 형식: Base64(방),Base64(파일명),MIME,크기,SHA-256
        String[] values = message.split(",", -1);
        if (values.length != 5) {
            throw new IllegalArgumentException("파일 시작 정보가 잘못됐습니다.");
        }

        String roomName = decode(values[0]);
        String fileName = decode(values[1]);
        String mimeType = values[2];
        int size = Integer.parseInt(values[3]);
        String hash = values[4];

        if (roomName.isBlank() || roomName.length() > 40
                || fileName.isBlank() || fileName.length() > 255
                || fileName.equals(".") || fileName.equals("..")
                || fileName.chars().anyMatch(Character::isISOControl)
                || mimeType.length() > 100
                || !mimeType.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")
                || size < 0 || size > ChatAttachmentDao.MAX_FILE_BYTES
                || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("파일 시작 정보가 잘못됐습니다.");
        }

        // 수신자가 파일명 대신 경로를 받지 않도록 한다.
        for (char forbidden : "<>:\"/\\|?*".toCharArray()) {
            if (fileName.indexOf(forbidden) >= 0) {
                throw new IllegalArgumentException("파일명이 잘못됐습니다.");
            }
        }

        return new FileUploadSession(
                roomName, fileName, mimeType, size, hash);
    }

    public void appendChunk(String message) {
        // 형식: 조각번호,Base64(최대 16KB의 파일 바이트)
        String[] values = message.split(",", 2);
        if (values.length != 2 || values[1].length() > 22_000) {
            throw new IllegalArgumentException("파일 조각 형식 오류");
        }

        int sequence = Integer.parseInt(values[0]);
        byte[] chunk = Base64.getDecoder().decode(values[1]);

        if (sequence != nextSequence
                || chunk.length == 0
                || chunk.length > CHUNK_BYTES
                || data.size() + chunk.length > expectedSize) {
            throw new IllegalArgumentException("파일 조각 순서 또는 크기 오류");
        }

        data.write(chunk, 0, chunk.length);
        nextSequence++;
    }

    public byte[] finish() {
        byte[] bytes = data.toByteArray();

        if (bytes.length != expectedSize
                || !sha256(bytes).equals(expectedSha256)) {
            throw new IllegalArgumentException(
                    "파일 크기 또는 SHA-256이 일치하지 않습니다.");
        }

        return bytes;
    }

    public String roomName() {
        return roomName;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256을 사용할 수 없습니다.", error);
        }
    }
}
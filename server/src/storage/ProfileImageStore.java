package storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ProfileImageStore {
    private static final int MAX_BYTES = 256 * 1024;
    private static final Path ROOT = Path.of("uploads", "profiles").toAbsolutePath().normalize();
    private ProfileImageStore() {}

    public static BufferedImage decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        if (encoded.length() > ((MAX_BYTES + 2) / 3) * 4) {
            throw new IllegalArgumentException("Profile image is too large.");
        }
        byte[] data = Base64.getDecoder().decode(encoded);
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("Image is too large.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) throw new IllegalArgumentException("Invalid image.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!reader.getFormatName().equalsIgnoreCase("png")
                        || reader.getWidth(0) != 200 || reader.getHeight(0) != 200) {
                    throw new IllegalArgumentException("Expected a 200x200 PNG.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Invalid image.");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid image.", e);
        }
    }

    public static String save(BufferedImage image) throws IOException {
        if (image == null) return null;
        if (image.getWidth() != 200 || image.getHeight() != 200) {
            throw new IllegalArgumentException("Expected a 200x200 image.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable.");
        Files.createDirectories(ROOT);
        String name = UUID.randomUUID() + ".png";
        Path target = ROOT.resolve(name);
        try {
            Files.write(target, output.toByteArray(), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        System.out.println("Profile image saved: " + target);
        return "uploads/profiles/" + name;
    }

    public static void delete(String relativePath) throws IOException {
        if (relativePath == null) return;
        if (!relativePath.matches("uploads/profiles/[0-9a-f-]{36}\\.png")) {
            throw new IllegalArgumentException("Invalid profile image path.");
        }
        Files.deleteIfExists(ROOT.resolve(Path.of(relativePath).getFileName()));
    }
    
    //서버에 저장된 사진을 읽어 전송할 문자열로 변환한다.
    public static String readEncoded(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }

        if (!relativePath.matches(
                "uploads/profiles/[0-9a-f-]{36}\\.png")) {
            throw new IOException("올바르지 않은 프로필 사진 경로입니다.");
        }

        Path target = ROOT.resolve(Path.of(relativePath).getFileName());

        if (Files.isSymbolicLink(target)
                || Files.size(target) > MAX_BYTES) {
            throw new IOException("올바르지 않은 프로필 사진 파일입니다.");
        }

        byte[] bytes = Files.readAllBytes(target);
        String encoded = Base64.getEncoder().encodeToString(bytes);

        try {
            // 기존 decode()로 200×200 PNG인지 다시 확인
            decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IOException("저장된 사진 형식이 올바르지 않습니다.", e);
        }

        return encoded;
    }
}

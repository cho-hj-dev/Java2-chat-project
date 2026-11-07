package util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ProfileImages {
    public record PreparedPhoto(BufferedImage preview, String encoded) {}
    private ProfileImages() {}

    public static PreparedPhoto prepare(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 5L * 1024 * 1024) {
            throw new IOException("5MB 이하의 PNG 또는 JPG 파일을 선택하세요.");
        }
        BufferedImage source;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("이미지를 열 수 없습니다.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("지원하지 않는 이미지입니다.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) {
                    throw new IOException("PNG 또는 JPG 파일만 사용할 수 있습니다.");
                }
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || (long) w * h > 20_000_000L) {
                    throw new IOException("이미지는 2천만 화소 이하여야 합니다.");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (source == null) throw new IOException("이미지를 읽을 수 없습니다.");
        BufferedImage result = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 200, 200);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int side = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - side) / 2, y = (source.getHeight() - side) / 2;
            g.drawImage(source, 0, 0, 200, 200, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
            source.flush();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(result, "png", output)) throw new IOException("PNG 변환 실패");
        return new PreparedPhoto(result, Base64.getEncoder().encodeToString(output.toByteArray()));
    }
}

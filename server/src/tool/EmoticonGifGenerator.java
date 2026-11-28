package tool;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class EmoticonGifGenerator {

    private static final int FRAME_COUNT = 8;
    private static final int IMAGE_SIZE = 96;

    public static void main(String[] args) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("추가 GIF를 저장할 폴더 선택");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(null)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path folder = chooser.getSelectedFile().toPath();
        int created = 0;
        int skipped = 0;

        try {
            // GIF_01은 이미 만들었으므로 02, 03만 생성한다.
            for (int variant = 2; variant <= 3; variant++) {
                Path target = folder.resolve(
                        "GIF_0" + variant + ".gif");

                // 다시 실행해도 기존 GIF를 덮어쓰지 않는다.
                if (Files.exists(target)) {
                    skipped++;
                    continue;
                }

                generate(target, variant);
                created++;

                System.out.println(
                        "생성 완료: " + target.getFileName()
                        + " / " + Files.size(target) + "바이트");
            }

            JOptionPane.showMessageDialog(
                    null,
                    "추가 GIF 생성 완료\n"
                    + "새로 생성: " + created + "개\n"
                    + "이미 존재: " + skipped + "개\n"
                    + folder.toAbsolutePath());

        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    null,
                    "GIF 생성 실패: " + exception.getMessage(),
                    "오류",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private static void generate(Path target, int variant)
            throws IOException {
        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName("gif");

        if (!writers.hasNext()) {
            throw new IOException("GIF 저장 기능을 찾을 수 없습니다.");
        }

        ImageWriter writer = writers.next();

        try (ImageOutputStream output =
                ImageIO.createImageOutputStream(target.toFile())) {

            if (output == null) {
                throw new IOException("GIF 파일을 열 수 없습니다.");
            }

            writer.setOutput(output);
            writer.prepareWriteSequence(null);

            for (int frameNumber = 0;
                    frameNumber < FRAME_COUNT;
                    frameNumber++) {

            	BufferedImage frame = drawFrame(frameNumber, variant);
                ImageWriteParam parameter =
                        writer.getDefaultWriteParam();

                IIOMetadata metadata =
                        writer.getDefaultImageMetadata(
                                ImageTypeSpecifier
                                        .createFromRenderedImage(frame),
                                parameter);

                configureFrame(metadata, frameNumber == 0);

                writer.writeToSequence(
                        new IIOImage(frame, null, metadata),
                        parameter);
            }

            writer.endWriteSequence();

        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage drawFrame(
            int frameNumber, int variant) {

        BufferedImage image = new BufferedImage(
                IMAGE_SIZE,
                IMAGE_SIZE,
                BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
            graphics.setStroke(new BasicStroke(2.5f));

            if (variant == 2) {
                // GIF_02: 입 크기가 바뀌는 크게 웃는 얼굴.
                graphics.setColor(new Color(255, 211, 64));
                graphics.fillOval(18, 18, 60, 60);

                graphics.setColor(new Color(174, 115, 27));
                graphics.drawOval(18, 18, 60, 60);

                graphics.setColor(new Color(65, 45, 35));
                graphics.drawArc(34, 40, 12, 9, 0, 180);
                graphics.drawArc(52, 40, 12, 9, 0, 180);

                int mouthHeight = 10 + (frameNumber % 4) * 3;
                graphics.fillOval(38, 52, 20, mouthHeight);

            } else if (variant == 3) {
                // GIF_03: 프레임마다 하트가 커졌다 작아진다.
                int pulse = (int) Math.round(
                        5 * Math.sin(
                                2 * Math.PI
                                * frameNumber / FRAME_COUNT));

                Path2D.Double heart = new Path2D.Double();
                heart.moveTo(48, 77 + pulse);
                heart.curveTo(
                        31, 65 + pulse, 14, 50,
                        23, 32 - pulse);
                heart.curveTo(
                        28, 20 - pulse, 41, 22 - pulse,
                        48, 33 - pulse);
                heart.curveTo(
                        55, 22 - pulse, 68, 20 - pulse,
                        73, 32 - pulse);
                heart.curveTo(
                        82, 50, 65, 65 + pulse,
                        48, 77 + pulse);
                heart.closePath();

                graphics.setColor(new Color(245, 70, 105));
                graphics.fill(heart);

                graphics.setColor(new Color(175, 35, 65));
                graphics.draw(heart);
            }

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private static void configureFrame(
            IIOMetadata metadata, boolean firstFrame)
            throws IOException {

        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root =
                (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode control = (IIOMetadataNode)
                root.getElementsByTagName(
                        "GraphicControlExtension").item(0);

        // GIF 지연 시간의 단위는 1/100초다. 10이면 프레임당 0.1초.
        control.setAttribute("delayTime", "10");
        control.setAttribute("disposalMethod", "none");

        if (firstFrame) {
            // NETSCAPE 확장: 마지막 프레임 뒤 처음부터 반복한다.
            IIOMetadataNode extensions =
                    new IIOMetadataNode("ApplicationExtensions");

            IIOMetadataNode loop =
                    new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            loop.setUserObject(new byte[] {1, 0, 0});

            extensions.appendChild(loop);
            root.appendChild(extensions);
        }

        metadata.setFromTree(format, root);
    }
}
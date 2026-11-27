package network;

import app.Application;
import dto.request.GalleryGetRequest;
import dto.type.DtoType;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public final class ImageGalleryClient {

    public static final ImageGalleryClient INSTANCE =
            new ImageGalleryClient();

    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int CHUNK_BYTES = 16 * 1024;

    private Transfer active;

    private ImageGalleryClient() {
    }

    // 한 번에 이미지 한 장을 받는다.
    private static class Transfer {
        final Component owner;
        final long messageId;
        final String imageName;

        int expectedSize = -1;
        String expectedHash;
        int nextSequence;
        ByteArrayOutputStream data;

        Transfer(Component owner, long messageId, String imageName) {
            this.owner = owner;
            this.messageId = messageId;
            this.imageName = imageName;
        }
    }

    public void request(
            Component owner, String roomName,
            long messageId, String imageName) {

        synchronized (this) {
            if (active != null) {
                JOptionPane.showMessageDialog(
                        owner, "이미 다른 이미지를 불러오는 중입니다.");
                return;
            }
            active = new Transfer(owner, messageId, imageName);
        }

        try {
            if (!Application.sender.sendMessage(
                    new GalleryGetRequest(roomName, messageId))) {
                fail("이미지 요청을 보내지 못했습니다.");
            }
        } catch (IllegalArgumentException exception) {
            fail(exception.getMessage());
        }
    }

    // MessageReceiver의 소켓 수신 스레드에서 호출한다.
    // 이미지 조립과 디코딩은 Swing 화면 스레드 밖에서 처리한다.
    public synchronized void onMessage(
            DtoType type, String message) {

        if (active == null) {
            return;
        }

        try {
            switch (type) {
                case GALLERY_IMAGE_BEGIN -> begin(message);
                case GALLERY_IMAGE_CHUNK -> append(message);
                case GALLERY_IMAGE_END -> finish(message);
                default -> {
                    // 갤러리 원본 응답 외의 메시지는 처리하지 않는다.
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            fail("이미지를 표시하지 못했습니다: "
                    + exception.getMessage());
        }
    }

    private void begin(String message) {
        // 메시지 ID,MIME,전체 크기,SHA-256
        String[] values = message.split(",", -1);

        if (values.length != 4
                || Long.parseLong(values[0]) != active.messageId
                || active.data != null) {
            throw new IllegalArgumentException(
                    "이미지 정보가 올바르지 않습니다.");
        }

        String mimeType = values[1];
        int size = Integer.parseInt(values[2]);

        if (!mimeType.equals("image/png")
                && !mimeType.equals("image/jpeg")
                && !mimeType.equals("image/gif")) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이미지 형식입니다.");
        }

        if (size <= 0 || size > MAX_BYTES
                || !values[3].matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "이미지 크기 또는 해시가 올바르지 않습니다.");
        }

        active.expectedSize = size;
        active.expectedHash = values[3];
        active.data = new ByteArrayOutputStream(
                Math.min(size, 64 * 1024));
    }

    private void append(String message) {
        // 메시지 ID,조각 순서,Base64(조각)
        String[] values = message.split(",", 3);

        if (values.length != 3
                || active.data == null
                || Long.parseLong(values[0]) != active.messageId
                || Integer.parseInt(values[1]) != active.nextSequence
                || values[2].length() > 22_000) {
            throw new IllegalArgumentException(
                    "이미지 조각 순서가 올바르지 않습니다.");
        }

        byte[] chunk = Base64.getDecoder().decode(values[2]);

        if (chunk.length == 0 || chunk.length > CHUNK_BYTES
                || active.data.size() + chunk.length
                        > active.expectedSize) {
            throw new IllegalArgumentException(
                    "이미지 조각 크기가 올바르지 않습니다.");
        }

        active.data.write(chunk, 0, chunk.length);
        active.nextSequence++;
    }

    private void finish(String message) throws IOException {
        // 성공: 메시지 ID,OK / 실패: 메시지 ID,ERROR
        String[] values = message.split(",", -1);

        if (values.length != 2
                || Long.parseLong(values[0]) != active.messageId) {
            throw new IOException("이미지 완료 응답이 올바르지 않습니다.");
        }

        if (!"OK".equals(values[1]) || active.data == null) {
            throw new IOException(
                    "서버에서 이미지를 제공하지 못했습니다.");
        }

        byte[] bytes = active.data.toByteArray();

        if (bytes.length != active.expectedSize
                || !sha256(bytes).equals(active.expectedHash)) {
            throw new IOException(
                    "이미지 크기 또는 해시가 일치하지 않습니다.");
        }

        BufferedImage image = ImageIO.read(
                new ByteArrayInputStream(bytes));

        if (image == null) {
            throw new IOException("이미지 파일을 해석할 수 없습니다.");
        }

        Transfer completed = active;
        active = null;

        // 화면 갱신은 Swing 화면 스레드에서 처리한다.
        SwingUtilities.invokeLater(() ->
                showPreview(completed.owner, completed.imageName, image));
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("이미지 해시를 확인할 수 없습니다.",
                    exception);
        }
    }

    private static void showPreview(
            Component owner, String imageName, BufferedImage image) {

        // 큰 이미지는 창에 들어오도록 비율을 유지해 축소한다.
        double scale = Math.min(
                1.0,
                Math.min(700.0 / image.getWidth(),
                         500.0 / image.getHeight()));

        int width = Math.max(
                1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(
                1, (int) Math.round(image.getHeight() * scale));

        BufferedImage preview = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = preview.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        JScrollPane scrollPane = new JScrollPane(
                new JLabel(new ImageIcon(preview)));
        scrollPane.setPreferredSize(new Dimension(
                Math.min(720, width + 20),
                Math.min(520, height + 20)));

        JOptionPane.showMessageDialog(
                owner,
                scrollPane,
                "이미지 미리보기: " + imageName,
                JOptionPane.PLAIN_MESSAGE);
    }

    public synchronized void connectionClosed() {
        if (active != null) {
            fail("서버 연결이 종료되어 이미지 수신이 중단됐습니다.");
        }
    }

    private void fail(String message) {
        if (active == null) {
            return;
        }

        Component owner = active.owner;
        active = null;

        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(owner, message));
    }
}
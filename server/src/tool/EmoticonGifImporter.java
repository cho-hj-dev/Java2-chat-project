package tool;

import dao.EmoticonAssetDao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.util.Map;

public class EmoticonGifImporter {

    // 파일 이름에 따라 서로 다른 DB ID와 메뉴 이름을 사용한다.
    private static final Map<String, String> GIF_NAMES = Map.of(
            "GIF_01", "움직이는 이모티콘",
            "GIF_02", "크게 웃는 이모티콘",
            "GIF_03", "두근두근 하트"
    );
    private static final int MAX_BYTES = 1_048_576;
    private static final int MAX_SIDE = 256;

    public static void main(String[] args) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("DB에 등록할 움직이는 GIF 선택");
        chooser.setFileFilter(
                new FileNameExtensionFilter("GIF 이미지 (*.gif)", "gif"));

        if (chooser.showOpenDialog(null)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path file = chooser.getSelectedFile().toPath();
        String fileName = file.getFileName().toString();

        // 선택한 파일 이름에서 DB에 저장할 ID를 정한다.
        if (!fileName.matches("GIF_0[123]\\.gif")) {
            JOptionPane.showMessageDialog(
                    null,
                    "GIF_01.gif, GIF_02.gif, GIF_03.gif 중 하나를 선택해 주세요.");
            return;
        }

        String id = fileName.substring(0, fileName.length() - 4);
        String displayName = GIF_NAMES.get(id);

        try {
            long fileSize = Files.size(file);
            if (fileSize == 0 || fileSize > MAX_BYTES) {
                throw new IllegalArgumentException(
                        "GIF 파일은 1MB 이하여야 합니다.");
            }

            byte[] bytes = Files.readAllBytes(file);
            int frameCount = validateAnimatedGif(bytes);

            EmoticonAssetDao dao = new EmoticonAssetDao();

            if (dao.findById(id).isPresent()) {
                JOptionPane.showMessageDialog(
                        null, id + "은(는) 이미 DB에 등록돼 있습니다.");
                return;
            }

            // 파일마다 고유 ID로 등록한다. 기존 GIF_01은 덮어쓰지 않는다.
            dao.insert(id, displayName, "image/gif", bytes);

            JOptionPane.showMessageDialog(
                    null,
                    "GIF 이모티콘 등록 완료\n"
                    + "ID: " + id + "\n"
                    + "프레임: " + frameCount + "개\n"
                    + "크기: " + bytes.length + "바이트");

        } catch (IOException | SQLException
                | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    null,
                    "GIF 등록 실패: " + exception.getMessage(),
                    "등록 오류",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int validateAnimatedGif(byte[] bytes)
            throws IOException {

        try (ImageInputStream input =
                ImageIO.createImageInputStream(
                        new ByteArrayInputStream(bytes))) {

            if (input == null) {
                throw new IllegalArgumentException(
                        "이미지 파일을 읽을 수 없습니다.");
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "올바른 GIF 파일이 아닙니다.");
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(input);

                if (!"gif".equalsIgnoreCase(
                        reader.getFormatName())) {
                    throw new IllegalArgumentException(
                            "GIF 형식의 파일을 선택해 주세요.");
                }

                int frameCount = reader.getNumImages(true);

                if (frameCount < 2) {
                    throw new IllegalArgumentException(
                            "움직이는 GIF가 아닙니다. 프레임이 2개 이상이어야 합니다.");
                }

                // 첫 프레임뿐 아니라 모든 프레임의 크기를 확인한다.
                for (int i = 0; i < frameCount; i++) {
                    int width = reader.getWidth(i);
                    int height = reader.getHeight(i);

                    if (width <= 0 || height <= 0
                            || width > MAX_SIDE
                            || height > MAX_SIDE) {
                        throw new IllegalArgumentException(
                                "GIF의 모든 프레임은 256×256 이하여야 합니다.");
                    }
                }

                return frameCount;

            } finally {
                reader.dispose();
            }
        }
    }
}
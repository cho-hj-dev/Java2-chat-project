package view.style;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class EmoticonPngExporter {

    public static void main(String[] args) {
        // 저장할 폴더를 사용자가 직접 고른다.
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("이모티콘 PNG 저장 폴더 선택");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path folder = chooser.getSelectedFile().toPath();

        try {
            for (String id : EmoticonIcons.IDS) {
                ImageIcon icon = EmoticonIcons.iconFor(id);

                if (icon == null) {
                    throw new IOException("이모티콘을 찾지 못했습니다: " + id);
                }

                // 기존 Java2D 이모티콘을 PNG로 기록할 이미지에 그린다.
                BufferedImage image = new BufferedImage(
                        icon.getIconWidth(),
                        icon.getIconHeight(),
                        BufferedImage.TYPE_INT_ARGB);

                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.drawImage(icon.getImage(), 0, 0, null);
                } finally {
                    graphics.dispose();
                }

                Path output = folder.resolve(id + ".png");
                if (!ImageIO.write(image, "png", output.toFile())) {
                    throw new IOException("PNG 저장에 실패했습니다: " + output);
                }
            }

            JOptionPane.showMessageDialog(
                    null,
                    "PNG 6개를 저장했습니다.\n" + folder.toAbsolutePath());

        } catch (IOException error) {
            JOptionPane.showMessageDialog(
                    null,
                    error.getMessage(),
                    "PNG 저장 오류",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
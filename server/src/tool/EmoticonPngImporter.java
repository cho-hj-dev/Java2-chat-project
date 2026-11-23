package tool;

import dao.EmoticonAssetDao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class EmoticonPngImporter {

    public static void main(String[] args) {
        // 기존 채팅 기록에 저장된 ID를 그대로 사용한다.
        Map<String, String> names = new LinkedHashMap<>();
        names.put("SMILE", "웃음");
        names.put("LAUGH", "크게 웃음");
        names.put("WINK", "윙크");
        names.put("SAD", "슬픔");
        names.put("ANGRY", "화남");
        names.put("HEART", "하트");

        // Eclipse 실행 위치에 영향을 받지 않도록 폴더를 직접 선택한다.
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("DB에 등록할 이모티콘 PNG 폴더 선택");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path folder = chooser.getSelectedFile().toPath();
        EmoticonAssetDao dao = new EmoticonAssetDao();
        int inserted = 0;
        int skipped = 0;

        try {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                String id = entry.getKey();

                // 다시 실행해도 이미 등록된 ID 때문에 중단되지 않게 한다.
                if (dao.findById(id).isPresent()) {
                    skipped++;
                    continue;
                }

                Path imagePath = folder.resolve(id + ".png");
                byte[] imageData = Files.readAllBytes(imagePath);

                // DAO가 크기와 PNG 파일 형식을 검사한 뒤 DB에 저장한다.
                dao.insert(
                        id,
                        entry.getValue(),
                        "image/png",
                        imageData);

                inserted++;
                System.out.println("등록 완료: " + imagePath.getFileName());
            }

            JOptionPane.showMessageDialog(
                    null,
                    "DB 등록 완료\n새로 등록: " + inserted
                            + "개\n이미 등록됨: " + skipped + "개");

        } catch (IOException | SQLException | IllegalArgumentException error) {
            JOptionPane.showMessageDialog(
                    null,
                    "DB 등록 중 오류가 발생했습니다.\n"
                            + error.getMessage(),
                    "이모티콘 등록 오류",
                    JOptionPane.ERROR_MESSAGE);
            error.printStackTrace();
        }
    }
}
package view.frame;

import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class NotepadFrame extends JFrame {

    private final JTextArea textArea = new JTextArea();
    private Path currentFile; // 현재 열거나 저장한 파일
    private boolean dirty;    // 마지막 저장 이후 내용이 바뀌었는지

    public NotepadFrame() {
        super("새 문서 - 메모장");

        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        setContentPane(new JScrollPane(textArea));

        // 사용자가 글을 수정하면 제목에 *를 붙여 미저장 상태를 표시한다.
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                markDirty();
            }
        });

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("파일");

        JMenuItem newItem = new JMenuItem("새 문서");
        newItem.addActionListener(event -> newDocument());
        fileMenu.add(newItem);

        JMenuItem openItem = new JMenuItem("열기");
        openItem.addActionListener(event -> openDocument());
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("저장");
        saveItem.addActionListener(event -> saveDocument());
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("다른 이름으로 저장");
        saveAsItem.addActionListener(event -> saveDocumentAs());
        fileMenu.add(saveAsItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // 창을 닫을 때 미저장 내용이 있으면 저장 여부를 확인한다.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (confirmSaveIfDirty()) {
                    dispose();
                }
            }
        });

        setVisible(true);
    }

    private void markDirty() {
        dirty = true;
        updateTitle();
    }

    private void updateTitle() {
        String fileName = currentFile == null
                ? "새 문서"
                : currentFile.getFileName().toString();

        setTitle((dirty ? "* " : "") + fileName + " - 메모장");
    }

    private void newDocument() {
        if (!confirmSaveIfDirty()) {
            return;
        }

        currentFile = null;
        textArea.setText("");
        dirty = false;
        updateTitle();
    }

    private void openDocument() {
        if (!confirmSaveIfDirty()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        // 텍스트 파일을 기본으로 표시한다. 필요하면 All Files도 선택할 수 있다.
        chooser.setFileFilter(
                new FileNameExtensionFilter("텍스트 파일 (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path file = chooser.getSelectedFile().toPath();

        try {
            // 한글도 동일하게 읽히도록 UTF-8을 명시한다.
            String content = Files.readString(file, StandardCharsets.UTF_8);
            textArea.setText(content);
            currentFile = file;
            dirty = false;
            updateTitle();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "파일을 열지 못했습니다:\n" + exception.getMessage(),
                    "열기 오류",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean saveDocument() {
        if (currentFile == null) {
            return saveDocumentAs();
        }
        return saveTo(currentFile);
    }

    private boolean saveDocumentAs() {
        JFileChooser chooser = new JFileChooser();
        // 텍스트 파일을 기본으로 표시한다. 필요하면 All Files도 선택할 수 있다.
        chooser.setFileFilter(
                new FileNameExtensionFilter("텍스트 파일 (*.txt)", "txt"));

        if (currentFile != null) {
            chooser.setSelectedFile(currentFile.toFile());
        }

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }

        Path file = chooser.getSelectedFile().toPath();
        
        // 텍스트 파일 형식을 선택하고 확장자를 생략하면 .txt를 붙인다.
        String fileName = file.getFileName().toString();
        if (chooser.getFileFilter() instanceof FileNameExtensionFilter
                && !fileName.contains(".")) {
            file = file.resolveSibling(fileName + ".txt");
        }

        // 기존 파일을 실수로 덮어쓰지 않도록 확인한다.
        if (Files.exists(file)) {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    "이미 존재하는 파일입니다. 덮어쓸까요?",
                    "덮어쓰기 확인",
                    JOptionPane.YES_NO_OPTION);

            if (answer != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        return saveTo(file);
    }

    private boolean saveTo(Path file) {
        try {
            Files.writeString(
                    file,
                    textArea.getText(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            currentFile = file;
            dirty = false;
            updateTitle();
            return true;
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "파일을 저장하지 못했습니다:\n" + exception.getMessage(),
                    "저장 오류",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean confirmSaveIfDirty() {
        if (!dirty) {
            return true;
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "저장하지 않은 변경사항이 있습니다. 저장할까요?",
                "변경사항 확인",
                JOptionPane.YES_NO_CANCEL_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            return saveDocument();
        }
        if (answer == JOptionPane.NO_OPTION) {
            return true;
        }
        return false; // 취소하거나 창을 닫은 경우 현재 문서를 유지한다.
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(NotepadFrame::new);
    }
}
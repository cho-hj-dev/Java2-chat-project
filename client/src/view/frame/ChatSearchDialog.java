package view.frame;

import app.Application;
import dto.request.ChatSearchRequest;
import dto.response.ChatSearchItem;
import dto.type.MessageType;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ChatSearchDialog extends JDialog {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String roomName;
    private final JTextField keywordField = new JTextField(24);
    private final JButton searchButton = new JButton("검색");
    private final JTextArea resultArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("검색어를 입력해 주세요.");

    // 응답이 끝나기 전 중복 검색을 막는다.
    private boolean pending = false;

    public ChatSearchDialog(JFrame owner, String roomName) {
        super(owner, roomName + " 대화 검색", false);
        this.roomName = roomName;

        setLayout(new BorderLayout(8, 8));
        setSize(540, 440);
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("검색어"));
        searchPanel.add(keywordField);
        searchPanel.add(searchButton);
        add(searchPanel, BorderLayout.NORTH);

        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        add(statusLabel, BorderLayout.SOUTH);

        searchButton.addActionListener(event -> requestSearch());
        keywordField.addActionListener(event -> requestSearch());

        setLocationRelativeTo(owner);
        setVisible(true);
    }

    private void requestSearch() {
        if (pending) {
            return;
        }

        ChatSearchRequest request;
        try {
            // DTO 생성자에서 빈 검색어와 길이를 검사한다.
            request = new ChatSearchRequest(
                    roomName, keywordField.getText());
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this, exception.getMessage());
            return;
        }

        resultArea.setText("");
        statusLabel.setText("검색 중...");
        pending = true;
        searchButton.setEnabled(false);

        if (!Application.sender.sendMessage(request)) {
            pending = false;
            searchButton.setEnabled(true);
            statusLabel.setText("검색 요청을 보내지 못했습니다.");
        }
    }

    public void addResult(ChatSearchItem item) {
        if (!pending) {
            return;
        }

        String prefix = item.messageType() == MessageType.WHISPER
                ? "[귓속말] "
                : "";

        resultArea.append(
                "[" + item.sentAt().format(TIME_FORMAT) + "] "
                + prefix
                + item.senderName()
                + " (" + item.senderLoginId() + "): "
                + item.content()
                + "\n\n");
    }

    public void finishSearch(boolean success, int resultCount) {
        if (!pending) {
            return;
        }

        pending = false;
        searchButton.setEnabled(true);

        if (!success) {
            statusLabel.setText("검색에 실패했습니다.");
        } else if (resultCount == 0) {
            statusLabel.setText("검색 결과가 없습니다.");
        } else {
            statusLabel.setText("검색 결과 " + resultCount + "건");
        }
    }
}
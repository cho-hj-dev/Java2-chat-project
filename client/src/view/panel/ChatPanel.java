package view.panel;

import app.Application;
import dto.request.MessageRequest;
import dto.type.MessageType;
import dto.response.ChatHistoryItem;

import view.frame.DrawingFrame;
import domain.User;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

public class ChatPanel extends JPanel implements ActionListener {

    private static final int MAX_PNG_BYTES = 256 * 1024;
    private static final DateTimeFormatter HISTORY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final List<ChatHistoryItem> historyItems = new ArrayList<>();
    private boolean historyLoaded = false;

    private final String chatRoomName;
    private final JFrame frame;

    // JTextPane은 글과 이미지 아이콘을 한 채팅 영역에 표시할 수 있다.
    private final JTextPane chatPane = new JTextPane();
    private final JTextField msgTextF = new JTextField(50);
    private final JButton sendBtn = new JButton("전송");
    private final JButton drawingBtn = new JButton("그림판");
    private final JButton whisperBtn = new JButton("귓속말");

	// 서버가 보내준 현재 방 참여자 목록이다.
	private List<User> roomUsers = new ArrayList<>();

    public ChatPanel(JFrame frame, String chatRoomName) {
        this.frame = frame;
        this.chatRoomName = chatRoomName;

        setLayout(null);
        setBounds(10, 10, 400, 500);

        chatPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBounds(10, 0, 380, 450);
        add(scrollPane);

        msgTextF.setBounds(10, 460, 150, 35);
        add(msgTextF);

        sendBtn.setBounds(165, 460, 65, 35);
        sendBtn.addActionListener(this);
        add(sendBtn);

        whisperBtn.setBounds(235, 460, 75, 35);
        whisperBtn.addActionListener(event -> sendWhisper());
        add(whisperBtn);

        drawingBtn.setBounds(315, 460, 75, 35);
        drawingBtn.addActionListener(event ->
                new DrawingFrame(frame, this::sendDrawing));
        add(drawingBtn);

        // 이전 대화 수신이 끝나기 전에는 새 메시지를 보내지 않는다.
        setComposerEnabled(false);
        frame.add(this);
    }

    public void addMessage(
            MessageType messageType, String userName, String message) {

        // 기록 전송 전에 도착한 실시간 메시지는 DB 기록에 포함된다.
        // 화면에 먼저 붙이면 기록을 표시할 때 중복될 수 있어 건너뛴다.
        if (!historyLoaded
                && (messageType == MessageType.CHAT
                    || messageType == MessageType.IMAGE
                    || messageType == MessageType.WHISPER)) {
            return;
        }

        switch (messageType) {
	        case WHISPER:
	            appendText("[귓속말] " + userName + ": " + message + "\n");
	            break;
            case ENTER:
            case EXIT:
                appendText(message + "\n");
                break;

            case CHAT:
                appendText("[" + userName
                        + "] 님이 보낸 메시지: " + message + "\n");
                break;

            case IMAGE:
                // 서버에서 전달받은 PNG를 채팅창에 표시한다.
                try {
                    byte[] png = Base64.getDecoder().decode(message);
                    if (png.length > MAX_PNG_BYTES) {
                        throw new IOException("이미지가 너무 큽니다.");
                    }

                    BufferedImage image = ImageIO.read(
                            new ByteArrayInputStream(png));
                    if (image == null
                            || image.getWidth() != 600
                            || image.getHeight() != 350) {
                        throw new IOException("올바른 그림이 아닙니다.");
                    }

                    appendImage("[" + userName + "] 님이 보낸 그림:", image);
                } catch (IllegalArgumentException | IOException exception) {
                    appendText("[그림을 표시하지 못했습니다.]\n");
                }
                break;

            default:
                break;
        }
    }
    
    public void beginHistory() {
        historyItems.clear();
        historyLoaded = false;
        setComposerEnabled(false);
    }

    public void addHistoryItem(ChatHistoryItem item) {
        if (!historyLoaded && historyItems.size() < 30) {
            historyItems.add(item);
        }
    }

    public void finishHistory(boolean success) {
        if (historyLoaded) {
            return;
        }

        if (success) {
            // 새 실시간 메시지는 기존 문서 뒤에 있을 수 있다.
            // 기록을 역순으로 앞에 삽입하면 최종 화면은 시간순이 된다.
            for (int i = historyItems.size() - 1; i >= 0; i--) {
                prependHistoryItem(historyItems.get(i));
            }
        } else {
            appendText("[이전 대화를 불러오지 못했습니다.]\n");
        }

        historyItems.clear();
        historyLoaded = true;
        setComposerEnabled(true);
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
    }

    public void markHistoryReady() {
        // 새 방은 아직 저장된 대화가 없으므로 즉시 입력을 허용한다.
        historyLoaded = true;
        setComposerEnabled(true);
    }

    private void setComposerEnabled(boolean enabled) {
        msgTextF.setEnabled(enabled);
        sendBtn.setEnabled(enabled);
        whisperBtn.setEnabled(enabled);
        drawingBtn.setEnabled(enabled);
    }

    private void prependHistoryItem(ChatHistoryItem item) {
        String prefix = "["
                + item.sentAt().format(HISTORY_TIME_FORMAT) + "] ";
        boolean mine = Application.me != null
                && Application.me.getId().equals(item.senderLoginId());

        switch (item.messageType()) {
            case CHAT:
                prependText(prefix
                        + (mine ? "내가 보낸 메시지: "
                                : "[" + item.senderName()
                                  + "] 님이 보낸 메시지: ")
                        + item.content() + "\n");
                break;

            case WHISPER:
                prependText(prefix
                        + (mine ? "[내 귓속말] "
                                : "[귓속말] "
                                  + item.senderName() + ": ")
                        + item.content() + "\n");
                break;

            case IMAGE:
                prependHistoryImage(
                        prefix
                        + (mine ? "내가 보낸 그림:"
                                : "[" + item.senderName()
                                  + "] 님이 보낸 그림:"),
                        item.content());
                break;

            default:
                break;
        }
    }

    private void prependHistoryImage(
            String title, String encodedPng) {

        try {
            byte[] png = Base64.getDecoder().decode(encodedPng);
            if (png.length > MAX_PNG_BYTES) {
                throw new IOException("그림 크기 초과");
            }

            BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(png));
            if (image == null
                    || image.getWidth() != 600
                    || image.getHeight() != 350) {
                throw new IOException("잘못된 그림 형식");
            }

            Image preview = image.getScaledInstance(
                    360, 210, Image.SCALE_SMOOTH);
            SimpleAttributeSet attributes =
                    new SimpleAttributeSet();
            StyleConstants.setIcon(
                    attributes, new ImageIcon(preview));

            StyledDocument document =
                    chatPane.getStyledDocument();
            // 앞에서부터 제목, 그림, 줄바꿈 순서로 보이게 삽입한다.
            document.insertString(0, "\n", null);
            document.insertString(0, " ", attributes);
            document.insertString(0, title + "\n", null);

        } catch (IllegalArgumentException
                | IOException
                | BadLocationException exception) {
            prependText(title
                    + "\n[그림을 표시하지 못했습니다.]\n");
        }
    }

    private void prependText(String text) {
        try {
            chatPane.getStyledDocument().insertString(
                    0, text, null);
        } catch (BadLocationException exception) {
            throw new IllegalStateException(
                    "이전 대화를 표시하지 못했습니다.",
                    exception);
        }
    }

    public void setRoomUsers(List<User> users) {
        // 화면의 대상 선택에 사용할 목록을 복사해 보관한다.
        roomUsers = new ArrayList<>(users);
    }
    
    public List<User> getRoomUsers() {
        // 초대 대상 목록에서 현재 방 참여자를 제외할 때 사용한다.
        return new ArrayList<>(roomUsers);
    }

    private void sendWhisper() {
        if (!historyLoaded) return;
        String message = msgTextF.getText();

        if (message.isBlank() || message.length() > 2000) {
            JOptionPane.showMessageDialog(
                    frame, "귓속말은 1~2000자로 입력해주세요.");
            return;
        }

        // 자신을 제외하고 현재 방에 있는 사용자만 선택지로 보여준다.
        List<User> recipients = new ArrayList<>();
        for (User user : roomUsers) {
            if (!user.getId().equals(Application.me.getId())) {
                recipients.add(user);
            }
        }

        if (recipients.isEmpty()) {
            JOptionPane.showMessageDialog(
                    frame, "귓속말을 보낼 다른 참여자가 없습니다.");
            return;
        }

        // 닉네임이 같아도 구분할 수 있도록 ID를 함께 표시한다.
        String[] choices = new String[recipients.size()];
        for (int i = 0; i < recipients.size(); i++) {
            User user = recipients.get(i);
            choices[i] = user.getName() + " (" + user.getId() + ")";
        }

        Object selected = JOptionPane.showInputDialog(
                frame,
                "귓속말을 받을 사람을 선택하세요.",
                "귓속말",
                JOptionPane.QUESTION_MESSAGE,
                null,
                choices,
                choices[0]);

        if (selected == null) {
            return; // 사용자가 선택 창을 취소했다.
        }

        for (int i = 0; i < choices.length; i++) {
            if (!choices[i].equals(selected)) {
                continue;
            }

            User recipient = recipients.get(i);

            // 서버가 해석하는 형식: 수신자ID|Base64(메시지)
            String encodedMessage = Base64.getEncoder().encodeToString(
                    message.getBytes(StandardCharsets.UTF_8));
            String payload = recipient.getId() + "|" + encodedMessage;

            boolean sent = Application.sender.sendMessage(
                    new MessageRequest(
                            MessageType.WHISPER,
                            chatRoomName,
                            Application.me.getName(),
                            payload));

            if (sent) {
                appendText("[내 귓속말 → " + recipient.getName()
                        + "] " + message + "\n");
                msgTextF.setText("");
            } else {
                JOptionPane.showMessageDialog(
                        frame, "귓속말을 전송하지 못했습니다.");
            }
            return;
        }
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (!historyLoaded) return;
        String message = msgTextF.getText();

        if (!message.isEmpty()) {
            boolean sent = Application.sender.sendMessage(
                    new MessageRequest(
                            MessageType.CHAT,
                            chatRoomName,
                            Application.me.getName(),
                            message));

            if (sent) {
                appendText("내가 보낸 메시지: " + message + "\n");
                msgTextF.setText("");
            } else {
                JOptionPane.showMessageDialog(
                        frame, "메시지를 전송하지 못했습니다.");
            }
        }
    }

    private void sendDrawing(BufferedImage drawing) {
        if (!historyLoaded) return;
        try {
            // 그림판에서 받은 그림을 PNG 바이트로 변환한다.
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(drawing, "png", output)) {
                throw new IOException("PNG 변환에 실패했습니다.");
            }

            byte[] png = output.toByteArray();
            if (png.length > MAX_PNG_BYTES) {
                JOptionPane.showMessageDialog(
                        frame, "그림이 너무 복잡합니다. 조금 단순하게 그려주세요.");
                return;
            }

            // 기존 줄 단위 채팅 프로토콜로 보내기 위해 Base64로 인코딩한다.
            String encoded = Base64.getEncoder().encodeToString(png);
            boolean sent = Application.sender.sendMessage(
                    new MessageRequest(
                            MessageType.IMAGE,
                            chatRoomName,
                            Application.me.getName(),
                            encoded));

            if (sent) {
                appendImage("내가 보낸 그림:", drawing);
            } else {
                JOptionPane.showMessageDialog(
                        frame, "그림을 전송하지 못했습니다.");
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    frame, "그림을 PNG로 변환하지 못했습니다.");
        }
    }

    private void appendText(String text) {
        StyledDocument document = chatPane.getStyledDocument();
        try {
            document.insertString(document.getLength(), text, null);
            chatPane.setCaretPosition(document.getLength());
        } catch (BadLocationException exception) {
            throw new IllegalStateException(
                    "채팅 문구를 표시하지 못했습니다.", exception);
        }
    }

    private void appendImage(String title, BufferedImage drawing) {
        appendText(title + "\n");

        // 원본 600×350 그림을 채팅창 너비에 맞춰 축소해서 보여준다.
        Image preview = drawing.getScaledInstance(
                360, 210, Image.SCALE_SMOOTH);

        StyledDocument document = chatPane.getStyledDocument();
        chatPane.setCaretPosition(document.getLength());
        chatPane.insertIcon(new ImageIcon(preview));
        appendText("\n");
    }
    
    public JFrame getFrame() {
        return frame;
    }
}
package view.panel;

import app.Application;
import dto.request.MessageRequest;
import dto.type.MessageType;
import view.frame.DrawingFrame;

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

public class ChatPanel extends JPanel implements ActionListener {

    private static final int MAX_PNG_BYTES = 256 * 1024;

    private final String chatRoomName;
    private final JFrame frame;

    // JTextPane은 글과 이미지 아이콘을 한 채팅 영역에 표시할 수 있다.
    private final JTextPane chatPane = new JTextPane();
    private final JTextField msgTextF = new JTextField(50);
    private final JButton sendBtn = new JButton("전송");
    private final JButton drawingBtn = new JButton("그림판");

    public ChatPanel(JFrame frame, String chatRoomName) {
        this.frame = frame;
        this.chatRoomName = chatRoomName;

        setLayout(null);
        setBounds(10, 10, 400, 500);

        chatPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBounds(10, 0, 380, 450);
        add(scrollPane);

        msgTextF.setBounds(10, 460, 210, 35);
        add(msgTextF);

        sendBtn.setBounds(230, 460, 75, 35);
        sendBtn.addActionListener(this);
        add(sendBtn);

        drawingBtn.setBounds(310, 460, 80, 35);
        drawingBtn.addActionListener(event ->
                new DrawingFrame(frame, this::sendDrawing));
        add(drawingBtn);

        frame.add(this);
    }

    public void addMessage(
            MessageType messageType, String userName, String message) {

        switch (messageType) {
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

    @Override
    public void actionPerformed(ActionEvent event) {
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
}
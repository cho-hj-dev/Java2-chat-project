package view.frame;

import app.Application;
import dto.request.PublicProfileRequest;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** 다른 회원의 공개 정보만 보여주는 읽기 전용 화면. */
public class PublicProfileFrame extends JFrame {
    private final String targetLoginId;
    private final JLabel photoLabel = new JLabel("사진 조회 중...", SwingConstants.CENTER);
    private final JLabel nameLabel = new JLabel("조회 중...", SwingConstants.CENTER);
    private final JTextArea statusMessage = new JTextArea();
    private final JScrollPane statusScroll = new JScrollPane(statusMessage);
    private final JLabel joinedLabel = new JLabel("가입 시각 조회 중...", SwingConstants.CENTER);
    private final JLabel resultLabel = new JLabel("공개 프로필을 불러오는 중...", SwingConstants.CENTER);
    private String pendingRequestId;
    private final Timer timeout = new Timer(15000, e -> {
        pendingRequestId = null;
        resultLabel.setText("조회 시간이 초과됐습니다. 창을 다시 열어주세요.");
    });

    public PublicProfileFrame(JFrame owner, String targetLoginId, String displayName) {
        this.targetLoginId = targetLoginId;
        setTitle(displayName + "님의 프로필");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        setContentPane(content);

        photoLabel.setPreferredSize(new Dimension(200, 200));
        photoLabel.setBorder(BorderFactory.createEtchedBorder());
        JPanel photoWrapper = new JPanel();
        photoWrapper.add(photoLabel);
        content.add(photoWrapper, BorderLayout.NORTH);

        JPanel details = new JPanel(new BorderLayout(0, 12));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));
        details.add(nameLabel, BorderLayout.NORTH);
        statusMessage.setEditable(false);
        statusMessage.setFocusable(false);
        statusMessage.setLineWrap(true);
        statusMessage.setWrapStyleWord(true);
        statusMessage.setMargin(new Insets(6, 8, 6, 8));
        statusMessage.setText("상태 메시지 조회 중...");
        statusScroll.setPreferredSize(new Dimension(380, 90));
        statusScroll.setBorder(BorderFactory.createEtchedBorder());
        statusScroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        details.add(statusScroll, BorderLayout.CENTER);
        details.add(joinedLabel, BorderLayout.SOUTH);
        content.add(details, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(resultLabel, BorderLayout.NORTH);
        JButton close = new JButton("닫기");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel();
        buttons.add(close);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        timeout.setRepeats(false);
        setSize(460, 520);
        setLocationRelativeTo(owner);
        Application.publicProfileFrame = this;
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                timeout.stop();
                pendingRequestId = null;
                if (Application.publicProfileFrame == PublicProfileFrame.this) {
                    Application.publicProfileFrame = null;
                }
            }
        });
        SwingUtilities.invokeLater(this::loadProfile);
    }

    public String targetLoginId() {
        return targetLoginId;
    }

    private void loadProfile() {
        if (!isDisplayable()) return;
        pendingRequestId = UUID.randomUUID().toString();
        if (!Application.sender.sendMessage(
                new PublicProfileRequest(pendingRequestId, targetLoginId))) {
            pendingRequestId = null;
            resultLabel.setText("조회 요청을 보내지 못했습니다.");
            return;
        }
        timeout.restart();
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public void showProfileResult(String message) {
        String[] fields = message.split(",", -1);
        if (pendingRequestId == null || !isDisplayable()
                || fields.length < 2 || !pendingRequestId.equals(fields[0])) return;
        timeout.stop();
        pendingRequestId = null;
        if (fields.length != 8 || !"SUCCESS".equals(fields[1])) {
            resultLabel.setText("공개 프로필을 조회하지 못했습니다.");
            return;
        }
        try {
            if (!targetLoginId.equals(decode(fields[2]))) {
                throw new IllegalArgumentException("Account mismatch");
            }
            String nickname = decode(fields[3]);
            String messageText = decode(fields[4]);
            LocalDateTime joined = LocalDateTime.parse(decode(fields[5]));
            ImageIcon photo = null;
            if ("OK".equals(fields[6])) {
                if (fields[7].length() > 349528) throw new IllegalArgumentException();
                byte[] bytes = Base64.getDecoder().decode(fields[7]);
                if (bytes.length > 262144) throw new IllegalArgumentException();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null || image.getWidth() != 200
                        || image.getHeight() != 200) throw new IllegalArgumentException();
                photo = new ImageIcon(image);
            } else if (!"NONE".equals(fields[6]) && !"MISSING".equals(fields[6])) {
                throw new IllegalArgumentException();
            }
            nameLabel.setText(nickname);
            statusMessage.setText(messageText);
            statusMessage.setCaretPosition(0);
            statusScroll.setVisible(!messageText.isBlank());
            joinedLabel.setText("가입 시각: " + joined.format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            photoLabel.setIcon(photo);
            photoLabel.setText(photo == null ? "등록된 프로필 사진 없음" : "");
            resultLabel.setText("MISSING".equals(fields[6])
                    ? "서버의 사진 파일을 불러올 수 없습니다." : "");
        } catch (Exception e) {
            resultLabel.setText("프로필 정보를 표시하지 못했습니다.");
        }
    }

    public void connectionFailed() {
        timeout.stop();
        pendingRequestId = null;
        resultLabel.setText("서버 연결이 종료됐습니다.");
    }
}

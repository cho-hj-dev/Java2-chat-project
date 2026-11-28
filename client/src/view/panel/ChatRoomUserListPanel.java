package view.panel;

import app.Application;
import domain.User;
import view.frame.PublicProfileFrame;
import view.frame.LobbyFrame;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.BoxLayout;
import java.awt.Dimension;
import java.util.List;

public class ChatRoomUserListPanel extends JPanel {

    private final JPanel labelPanel = new JPanel();
    private final JLabel label = new JLabel("채팅방 사용자 목록");
    private final JLabel weatherLabel = new JLabel("날씨: 조회 중...");
    private final JFrame owner;

    public ChatRoomUserListPanel(JFrame frame) {
        this.owner = frame;
        setLayout(null);

        // 로비에서는 제목 공간의 절반을 날씨 표시에 사용한다.
        if (frame instanceof LobbyFrame) {
            label.setBounds(0, 0, 400, 25);
            weatherLabel.setBounds(0, 25, 400, 25);
            add(weatherLabel);
        } else {
            label.setBounds(0, 0, 400, 50);
        }
        add(label);

        labelPanel.setSize(400, 150);
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(labelPanel);
        scrollPane.setBounds(0, 50, 400, 150);
        add(scrollPane);

        frame.add(this);
        setBounds(410, 10, 400, 200);
    }

    // 서버 응답을 받은 뒤 로비의 날씨 문구를 갱신한다.
    public void setWeatherText(String text) {
        weatherLabel.setText(text);
    }

    public void paintChatUsers(List<User> chatUsers) {
        labelPanel.removeAll();

        for (User user : chatUsers) {
            boolean isMe = Application.me != null
                    && Application.me.getId().equals(user.getId());
            JButton userButton = new JButton(
                    user.getName() + (isMe ? " (나)" : ""));
            userButton.setHorizontalAlignment(SwingConstants.LEFT);
            userButton.setAlignmentX(LEFT_ALIGNMENT);
            userButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            userButton.setPreferredSize(new Dimension(380, 28));
            userButton.setToolTipText("프로필 보기: " + user.getName());
            userButton.addActionListener(e -> {
                if (Application.me == null) return;
                PublicProfileFrame current = Application.publicProfileFrame;
                if (current != null && current.isDisplayable()) {
                    if (user.getId().equals(current.targetLoginId())) {
                        current.toFront();
                        return;
                    }
                    current.dispose();
                }
                PublicProfileFrame profile = new PublicProfileFrame(
                        owner, user.getId(), user.getName());
                profile.setVisible(true);
            });
            labelPanel.add(userButton);
        }

        labelPanel.revalidate();
        labelPanel.repaint();
    }
}

package view.panel;

import domain.User;
import view.frame.LobbyFrame;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.GridLayout;
import java.awt.Label;
import java.util.List;

public class ChatRoomUserListPanel extends JPanel {

    private final JPanel labelPanel = new JPanel();
    private final JLabel label = new JLabel("사용자 목록");
    private final JLabel weatherLabel = new JLabel("날씨: 조회 중...");

    public ChatRoomUserListPanel(JFrame frame) {
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
        labelPanel.setLayout(new GridLayout(50, 1));

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
            labelPanel.add(new Label(user.getName()));
        }

        labelPanel.revalidate();
        labelPanel.repaint();
    }
}
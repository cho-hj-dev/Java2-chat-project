package view.frame;

import app.Application;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

/** 서버의 관리자 권한 확인 응답을 받은 뒤 여는 관리 화면. */
public class AdminFrame extends JFrame {
    public AdminFrame(JFrame owner) {
        super("관리자 화면");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(new JLabel("관리자 기능", SwingConstants.CENTER),
                BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("채팅방", placeholder("채팅방 관리"));
        tabs.addTab("메시지", placeholder("메시지 관리"));
        tabs.addTab("회원", placeholder("회원 관리"));
        content.add(tabs, BorderLayout.CENTER);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (Application.adminFrame == AdminFrame.this) {
                    Application.adminFrame = null;
                }
            }
        });
        setSize(650, 460);
        setLocationRelativeTo(owner);
    }

    private static JPanel placeholder(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title, SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }
}

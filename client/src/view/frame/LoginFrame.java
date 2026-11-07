package view.frame;

import app.Application;
import domain.User;
import dto.request.LoginRequest;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

public class LoginFrame extends JFrame implements ActionListener {
    private final LobbyFrame lobbyFrame;
    private final JTextField idTextF = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JButton loginBtn = new JButton("로그인");
    private final JButton signupBtn = new JButton("회원가입");
    private boolean loginPending;

    public LoginFrame(LobbyFrame lobbyFrame) {
        this.lobbyFrame = lobbyFrame;
        Application.loginFrame = this;
        setTitle("로그인");
        setLayout(null);
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel idLabel = new JLabel("아이디");
        idLabel.setBounds(100, 50, 100, 50);
        add(idLabel);
        idTextF.setBounds(200, 50, 300, 50);
        add(idTextF);
        JLabel passwordLabel = new JLabel("비밀번호");
        passwordLabel.setBounds(100, 120, 100, 50);
        add(passwordLabel);
        passwordField.setBounds(200, 120, 300, 50);
        add(passwordField);
        loginBtn.setBounds(150, 220, 300, 50);
        loginBtn.addActionListener(this);
        add(loginBtn);
        signupBtn.setBounds(150, 280, 300, 50);
        signupBtn.addActionListener(e -> {
            if (loginPending) return;
            new SignupFrame(this);
            setVisible(false);
        });
        add(signupBtn);
        getRootPane().setDefaultButton(loginBtn);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (loginPending) return;
        String loginId = idTextF.getText().trim();
        char[] password = passwordField.getPassword();
        try {
            if (!loginId.matches("[A-Za-z0-9_]{4,20}")) {
                showError("아이디는 영문·숫자·_ 조합으로 4~20자 입력하세요.");
                return;
            }
            if (password.length == 0 || password.length > 20) {
                showError("비밀번호를 확인하세요.");
                return;
            }
            setLoginPending(true);
            if (Application.sender == null || !Application.sender.sendMessage(
                    new LoginRequest(loginId, new String(password)))) {
                setLoginPending(false);
                showError("로그인 요청 전송에 실패했습니다. 서버 연결을 확인하세요.");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void setLoginPending(boolean pending) {
        loginPending = pending;
        idTextF.setEditable(!pending);
        passwordField.setEditable(!pending);
        loginBtn.setEnabled(!pending);
        signupBtn.setEnabled(!pending);
        loginBtn.setText(pending ? "로그인 중..." : "로그인");
    }

    public void showLoginResult(String status, String loginId, String nickname) {
        if (!isDisplayable() || !loginPending) return;
        if ("SUCCESS".equals(status)) {
            if (!idTextF.getText().trim().equals(loginId)) {
                setLoginPending(false);
                showError("로그인 응답의 아이디가 일치하지 않습니다.");
                return;
            }
            Application.me = new User(loginId, nickname);
            passwordField.setText("");
            Application.loginFrame = null;
            dispose();
            lobbyFrame.setVisible(true);
            return;
        }
        setLoginPending(false);
        passwordField.setText("");
        showError(switch (status) {
            case "INVALID_CREDENTIALS" -> "아이디 또는 비밀번호가 올바르지 않습니다.";
            case "ALREADY_LOGGED_IN" -> "이미 로그인한 계정입니다.";
            default -> "로그인 처리에 실패했습니다. 서버 연결을 확인하세요.";
        });
        passwordField.requestFocusInWindow();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "로그인 안내",
                JOptionPane.WARNING_MESSAGE);
    }
}

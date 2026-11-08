package view.frame;

import app.Application;
import dto.request.AccountRecoveryRequest;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ResetPasswordFrame extends JFrame {

    private final JTextField loginIdField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextField codeField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();

    private final JButton sendButton =
            new JButton("인증번호 발송");
    private final JButton resetButton =
            new JButton("비밀번호 변경");
    private final JLabel resultLabel =
            new JLabel("아이디와 가입 때 인증한 이메일을 입력하세요.");

    private long verificationId;
    private String pendingAction;

    public ResetPasswordFrame(LoginFrame loginFrame) {
        setTitle("비밀번호 재설정");
        setLayout(null);
        setSize(600, 460);
        setLocationRelativeTo(loginFrame);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addRow("아이디", loginIdField, 25);
        addRow("이메일", emailField, 75);
        addRow("인증번호", codeField, 125);
        addRow("새 비밀번호", passwordField, 180);
        addRow("새 비밀번호 확인", confirmField, 235);

        emailField.setBounds(160, 75, 250, 35);
        sendButton.setBounds(420, 75, 135, 35);
        sendButton.addActionListener(e -> sendCode());
        add(sendButton);

        resetButton.setBounds(160, 300, 190, 40);
        resetButton.setEnabled(false);
        resetButton.addActionListener(e -> resetPassword());
        add(resetButton);

        JButton backButton = new JButton("돌아가기");
        backButton.setBounds(365, 300, 190, 40);
        backButton.addActionListener(e -> dispose());
        add(backButton);

        resultLabel.setBounds(160, 350, 395, 30);
        add(resultLabel);

        DocumentListener identityListener = new DocumentListener() {
            private void reset() {
                verificationId = 0;
                resetButton.setEnabled(false);
                resultLabel.setText(
                        "아이디와 가입 때 인증한 이메일을 입력하세요.");
            }

            public void insertUpdate(DocumentEvent e) {
                reset();
            }

            public void removeUpdate(DocumentEvent e) {
                reset();
            }

            public void changedUpdate(DocumentEvent e) {
                reset();
            }
        };
        loginIdField.getDocument().addDocumentListener(
                identityListener);
        emailField.getDocument().addDocumentListener(
                identityListener);

        Application.resetPasswordFrame = this;
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (Application.resetPasswordFrame
                        == ResetPasswordFrame.this) {
                    Application.resetPasswordFrame = null;
                }
                loginFrame.setVisible(true);
            }
        });

        setVisible(true);
    }

    private void addRow(String labelText,
            javax.swing.JComponent field, int y) {
        JLabel label = new JLabel(labelText);
        label.setBounds(25, y, 125, 35);
        add(label);
        field.setBounds(160, y, 395, 35);
        add(field);
    }

    private void setPending(String action) {
        pendingAction = action;
        boolean pending = action != null;
        loginIdField.setEditable(!pending);
        emailField.setEditable(!pending);
        codeField.setEditable(!pending);
        passwordField.setEditable(!pending);
        confirmField.setEditable(!pending);
        sendButton.setEnabled(!pending);
        resetButton.setEnabled(!pending && verificationId > 0);
    }

    private void sendCode() {
        if (pendingAction != null) return;

        String loginId = loginIdField.getText().trim();
        String email = emailField.getText().trim();

        if (!loginId.matches("[A-Za-z0-9_]{4,20}")
                || email.length() > 254
                || !email.matches(
                        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            resultLabel.setText(
                    "아이디 또는 이메일 형식을 확인하세요.");
            return;
        }

        verificationId = 0;
        setPending("RESET_SEND");
        resultLabel.setText("인증번호 발송 중...");

        if (Application.sender == null
                || !Application.sender.sendMessage(
                        AccountRecoveryRequest.sendReset(
                                loginId, email))) {
            setPending(null);
            resultLabel.setText(
                    "발송 요청 실패. 서버 연결을 확인하세요.");
        }
    }

    private void resetPassword() {
        if (pendingAction != null || verificationId <= 0) return;

        String code = codeField.getText().trim();
        char[] password = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();

        try {
            String newPassword = new String(password);

            if (!code.matches("[0-9]{6}")) {
                resultLabel.setText(
                        "6자리 인증번호를 입력하세요.");
                return;
            }

            if (!newPassword.matches(
                    "^(?=.*[A-Za-z])(?=.*[0-9])"
                    + "(?=.*[^A-Za-z0-9\\s])\\S{8,20}$")) {
                resultLabel.setText(
                        "비밀번호는 8~20자, 영문·숫자·특수문자를 포함하세요.");
                return;
            }

            if (!Arrays.equals(password, confirm)) {
                resultLabel.setText(
                        "새 비밀번호 확인 값이 일치하지 않습니다.");
                return;
            }

            setPending("RESET_COMMIT");
            resultLabel.setText("비밀번호 변경 중...");

            if (Application.sender == null
                    || !Application.sender.sendMessage(
                            AccountRecoveryRequest.resetPassword(
                                    verificationId,
                                    loginIdField.getText().trim(),
                                    emailField.getText().trim(),
                                    code, newPassword))) {
                setPending(null);
                resultLabel.setText(
                        "변경 요청 실패. 서버 연결을 확인하세요.");
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    public void showResult(String message) {
        if (!isDisplayable() || pendingAction == null) return;

        String[] values = message.split(",", -1);
        if (values.length != 4
                || !pendingAction.equals(values[0])) {
            setPending(null);
            resultLabel.setText("응답 형식 오류");
            return;
        }

        String action = values[0];
        String status = values[1];
        setPending(null);

        if ("RESET_SEND".equals(action)) {
            if ("SENT".equals(status)) {
                try {
                    verificationId = Long.parseLong(values[2]);
                    resetButton.setEnabled(true);
                    resultLabel.setText(
                            "메일이 도착했다면 인증번호를 입력하세요.");
                } catch (NumberFormatException e) {
                    resultLabel.setText("인증 요청 번호 오류");
                }
            } else if ("WAIT".equals(status)) {
                resultLabel.setText(
                        "1분 후 다시 발송해 주세요.");
            } else {
                resultLabel.setText(
                        "발송 실패. 서버와 메일 설정을 확인하세요.");
            }
            return;
        }

        if (!Long.toString(verificationId).equals(values[2])) {
            resultLabel.setText(
                    "다른 인증 요청의 응답입니다.");
            return;
        }

        switch (status) {
            case "SUCCESS":
                passwordField.setText("");
                confirmField.setText("");
                JOptionPane.showMessageDialog(
                        this, "비밀번호가 변경되었습니다.");
                dispose();
                break;
            case "INVALID_CODE":
                resultLabel.setText(
                        "인증번호가 틀렸거나 만료되었습니다.");
                break;
            case "INVALID_INPUT":
                resultLabel.setText(
                        "입력값을 다시 확인하세요.");
                break;
            default:
                resultLabel.setText(
                        "비밀번호 변경에 실패했습니다.");
        }
    }
}
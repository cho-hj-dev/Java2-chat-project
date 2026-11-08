package view.frame;

import app.Application;
import dto.request.AccountRecoveryRequest;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class FindIdFrame extends JFrame {

    private final JTextField emailField = new JTextField();
    private final JTextField codeField = new JTextField();
    private final JButton sendButton = new JButton("인증번호 발송");
    private final JButton verifyButton = new JButton("아이디 확인");
    private final JLabel resultLabel =
            new JLabel("가입할 때 인증한 이메일을 입력하세요.");

    private long verificationId;
    private String pendingAction;

    public FindIdFrame(LoginFrame loginFrame) {
        setTitle("아이디 찾기");
        setLayout(null);
        setSize(530, 310);
        setLocationRelativeTo(loginFrame);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel emailLabel = new JLabel("이메일");
        emailLabel.setBounds(30, 30, 100, 35);
        add(emailLabel);

        emailField.setBounds(130, 30, 230, 35);
        add(emailField);

        sendButton.setBounds(370, 30, 120, 35);
        sendButton.addActionListener(e -> sendCode());
        add(sendButton);

        JLabel codeLabel = new JLabel("인증번호");
        codeLabel.setBounds(30, 90, 100, 35);
        add(codeLabel);

        codeField.setBounds(130, 90, 230, 35);
        add(codeField);

        verifyButton.setBounds(370, 90, 120, 35);
        verifyButton.setEnabled(false);
        verifyButton.addActionListener(e -> verifyCode());
        add(verifyButton);

        resultLabel.setBounds(130, 140, 360, 30);
        add(resultLabel);

        JButton closeButton = new JButton("돌아가기");
        closeButton.setBounds(190, 195, 150, 40);
        closeButton.addActionListener(e -> dispose());
        add(closeButton);

        emailField.getDocument().addDocumentListener(
                new DocumentListener() {
                    private void reset() {
                        verificationId = 0;
                        verifyButton.setEnabled(false);
                        resultLabel.setText(
                                "가입할 때 인증한 이메일을 입력하세요.");
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
                });

        Application.findIdFrame = this;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (Application.findIdFrame == FindIdFrame.this) {
                    Application.findIdFrame = null;
                }
                loginFrame.setVisible(true);
            }
        });

        setVisible(true);
    }

    private void setPending(String action) {
        pendingAction = action;
        emailField.setEditable(action == null);
        sendButton.setEnabled(action == null);
        verifyButton.setEnabled(
                action == null && verificationId > 0);
    }

    private void sendCode() {
        if (pendingAction != null) return;

        String email = emailField.getText().trim();
        if (!email.matches(
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                || email.length() > 254) {
            resultLabel.setText("이메일 형식을 확인하세요.");
            return;
        }

        verificationId = 0;
        setPending("FIND_ID_SEND");
        resultLabel.setText("인증번호 발송 중...");

        if (Application.sender == null
                || !Application.sender.sendMessage(
                        AccountRecoveryRequest.sendFindId(email))) {
            setPending(null);
            resultLabel.setText("발송 요청 실패. 서버 연결을 확인하세요.");
        }
    }

    private void verifyCode() {
        if (pendingAction != null || verificationId <= 0) return;

        String code = codeField.getText().trim();
        if (!code.matches("[0-9]{6}")) {
            resultLabel.setText("6자리 인증번호를 입력하세요.");
            return;
        }

        setPending("FIND_ID_VERIFY");
        resultLabel.setText("인증번호 확인 중...");

        if (Application.sender == null
                || !Application.sender.sendMessage(
                        AccountRecoveryRequest.verifyFindId(
                                verificationId,
                                emailField.getText().trim(),
                                code))) {
            setPending(null);
            resultLabel.setText("확인 요청 실패. 서버 연결을 확인하세요.");
        }
    }

    public void showResult(String message) {
        if (!isDisplayable() || pendingAction == null) return;

        String[] values = message.split(",", -1);
        if (values.length != 4
                || !pendingAction.equals(values[0])) {
            setPending(null);
            resultLabel.setText("응답 형식을 확인할 수 없습니다.");
            return;
        }

        String action = values[0];
        String status = values[1];
        setPending(null);

        if ("FIND_ID_SEND".equals(action)) {
            if ("SENT".equals(status)) {
                try {
                    verificationId = Long.parseLong(values[2]);
                    resultLabel.setText(
                            "메일로 받은 6자리 번호를 입력하세요.");
                    verifyButton.setEnabled(true);
                } catch (NumberFormatException e) {
                    resultLabel.setText("인증 요청 번호 오류");
                }
            } else if ("WAIT".equals(status)) {
                resultLabel.setText("1분 후 다시 발송해 주세요.");
            } else {
                resultLabel.setText(
                        "발송 실패. 서버와 메일 설정을 확인하세요.");
            }
            return;
        }

        if (!Long.toString(verificationId).equals(values[2])) {
            resultLabel.setText("다른 인증 요청의 응답입니다.");
            return;
        }

        switch (status) {
            case "VERIFIED": {
                String loginId = new String(
                        Base64.getDecoder().decode(values[3]),
                        StandardCharsets.UTF_8);
                resultLabel.setText("아이디 찾기 완료");
                verifyButton.setEnabled(false);
                JOptionPane.showMessageDialog(
                        this, "가입한 아이디: " + loginId);
                break;
            }
            case "NO_ACCOUNT":
                resultLabel.setText(
                        "이 이메일로 가입한 계정이 없습니다.");
                verifyButton.setEnabled(false);
                break;
            case "INVALID_CODE":
                resultLabel.setText(
                        "번호가 틀렸거나 만료되었습니다.");
                break;
            default:
                resultLabel.setText(
                        "확인 실패. 다시 시도해 주세요.");
        }
    }
}
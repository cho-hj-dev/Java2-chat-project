package view.frame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JComponent;

import app.Application;
import dto.request.CheckIdRequest;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.util.regex.Pattern;

public class SignupFrame extends JFrame {

    private final JTextField idField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();
    private final JTextField nicknameField = new JTextField();

    private final JLabel idResult = new JLabel("아이디 중복확인 필요");
    private final JButton checkIdButton = new JButton("중복확인");
    private final JButton signupButton = new JButton("가입");

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S{8,20}$");

    private final JLabel passwordResult = new JLabel("");
    private final JLabel confirmResult = new JLabel("");
    
    public SignupFrame(LoginFrame loginFrame) {
        setTitle("회원가입");
        setLayout(null);
        setSize(550, 410);
        setLocationRelativeTo(loginFrame);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addRow("아이디", idField, 35);
        addRow("비밀번호", passwordField, 125);
        addRow("비밀번호 확인", confirmField, 185);
        addRow("닉네임", nicknameField, 245);
        
        passwordResult.setBounds(190, 160, 305, 25);
        add(passwordResult);

        confirmResult.setBounds(190, 220, 305, 25);
        add(confirmResult);

        DocumentListener passwordListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePasswordResult();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePasswordResult();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePasswordResult();
            }
        };

        passwordField.getDocument().addDocumentListener(passwordListener);
        confirmField.getDocument().addDocumentListener(passwordListener);

        idField.setBounds(190, 35, 190, 35);
        checkIdButton.setBounds(390, 35, 105, 35);
        add(checkIdButton);

        idResult.setBounds(190, 75, 305, 30);
        add(idResult);

        signupButton.setBounds(190, 310, 140, 40);
        add(signupButton);

        JButton backButton = new JButton("돌아가기");
        backButton.setBounds(345, 310, 150, 40);
        backButton.addActionListener(e -> dispose());
        add(backButton);

        checkIdButton.setEnabled(true);

        idField.getDocument().addDocumentListener(new DocumentListener() {
            private void resetResult() {
                idResult.setText("아이디 중복확인 필요");
                checkIdButton.setEnabled(true);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                resetResult();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                resetResult();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                resetResult();
            }
        });

        checkIdButton.addActionListener(e -> {
            String loginId = idField.getText().trim();

            if (!loginId.matches("[A-Za-z0-9_]{4,20}")) {
                idResult.setText("영문·숫자·_ 조합으로 4~20자 입력");
                return;
            }

            idResult.setText("확인 중...");
            checkIdButton.setEnabled(false);
            Application.sender.sendMessage(new CheckIdRequest(loginId));
        });

        Application.signupFrame = this;
        signupButton.setEnabled(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
            	if (Application.signupFrame == SignupFrame.this) {
            	    Application.signupFrame = null;
            	}
                loginFrame.setVisible(true);
            }
        });

        setVisible(true);
    }
    
    public void showCheckIdResult(String checkedId, String status) { //결과 표시 메서드
        if (!idField.getText().trim().equals(checkedId)) {
            return;
        }

        checkIdButton.setEnabled(true);

        switch (status) {
            case "AVAILABLE":
                idResult.setText("사용 가능한 아이디입니다.");
                break;
            case "DUPLICATE":
                idResult.setText("이미 사용 중인 아이디입니다.");
                break;
            default:
                idResult.setText("확인에 실패했습니다. 다시 시도하세요.");
        }
    }
    
    private void updatePasswordResult() {
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());

        if (password.isEmpty()) {
            passwordResult.setText("");
        } else if (password.length() < 8 || password.length() > 20) {
            passwordResult.setText("비밀번호는 8~20자로 입력하세요.");
        } else if (!password.matches(".*[A-Za-z].*")) {
            passwordResult.setText("영문을 1자 이상 포함하세요.");
        } else if (!password.matches(".*[0-9].*")) {
            passwordResult.setText("숫자를 1자 이상 포함하세요.");
        } else if (!password.matches(".*[^A-Za-z0-9\\s].*")) {
            passwordResult.setText("특수문자를 1자 이상 포함하세요.");
        } else if (!PASSWORD_PATTERN.matcher(password).matches()) {
            passwordResult.setText("공백은 사용할 수 없습니다.");
        } else {
            passwordResult.setText("사용 가능한 비밀번호입니다.");
        }

        if (confirm.isEmpty()) {
            confirmResult.setText("");
        } else if (!confirm.equals(password)) {
            confirmResult.setText("비밀번호가 일치하지 않습니다.");
        } else {
            confirmResult.setText("비밀번호가 일치합니다.");
        }
    }

    private void addRow(String text, JComponent field, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(45, y, 135, 35);
        add(label);

        field.setBounds(190, y, 305, 35);
        add(field);
    }
}
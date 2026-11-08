package view.frame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import app.Application;
import dto.request.CheckIdRequest;
import dto.request.SignupRequest;
import dto.request.EmailCodeRequest;
import util.ProfileImages;
import javax.swing.filechooser.FileNameExtensionFilter;

public class SignupFrame extends JFrame {
    private final JTextField idField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();
    private final JTextField nicknameField = new JTextField();
    private final JTextField postalCodeField = new JTextField();
    private final JTextField addressField = new JTextField();
    private final JTextField addressDetailField = new JTextField();
    private final JLabel idResult = new JLabel("아이디 중복확인 필요");
    private final JLabel passwordResult = new JLabel("");
    private final JLabel confirmResult = new JLabel("");
    private final JButton checkIdButton = new JButton("중복확인");
    private final JButton signupButton = new JButton("가입");
    private final JButton backButton = new JButton("돌아가기");

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S{8,20}$");

    private final JLabel photoPreview = new JLabel("프로필 사진", SwingConstants.CENTER);
    private final JButton photoButton = new JButton("사진 선택");
    private final JButton removePhotoButton = new JButton("사진 제거");
    private String profileImage = "";
    private boolean imageLoading;
    private String checkedLoginId;
    private boolean signupPending;
    
    private final JTextField emailField = new JTextField();
    private final JTextField emailCodeField = new JTextField();

    private final JButton sendEmailCodeButton = new JButton("인증번호 발송");
    private final JButton verifyEmailCodeButton = new JButton("인증번호 확인");

    private final JLabel emailResult = new JLabel("이메일 인증이 필요합니다.");

    private String verifiedEmail;
    
    private long emailVerificationId;
    private boolean emailRequestPending;

    public SignupFrame(LoginFrame loginFrame) {
        setTitle("회원가입");
        setLayout(null);
        setSize(800, 710);
        setLocationRelativeTo(loginFrame);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addRow("아이디", idField, 35);
        addRow("비밀번호", passwordField, 125);
        addRow("비밀번호 확인", confirmField, 185);
        addRow("닉네임", nicknameField, 245);
        addRow("우편번호", postalCodeField, 305);
        addRow("기본주소", addressField, 365);
        addRow("상세주소", addressDetailField, 425);
        addRow("이메일", emailField, 485);
        addRow("인증번호", emailCodeField, 545);

        emailField.setBounds(190, 485, 190, 35);
        sendEmailCodeButton.setBounds(390, 485, 105, 35);
        add(sendEmailCodeButton);

        emailCodeField.setBounds(190, 545, 190, 35);
        verifyEmailCodeButton.setBounds(390, 545, 105, 35);
        add(verifyEmailCodeButton);

        emailResult.setBounds(190, 575, 305, 25);
        add(emailResult);

        // 서버 발송·검증 연결 후 활성화
        emailField.getDocument().addDocumentListener(new DocumentListener() {
            private void resetEmailResult() {
                verifiedEmail = null;
                emailVerificationId = 0;
                emailResult.setText("이메일 인증이 필요합니다.");
                updateEmailButtons();
                updateSignupButton();
            }

            public void insertUpdate(DocumentEvent e) {
                resetEmailResult();
            }

            public void removeUpdate(DocumentEvent e) {
                resetEmailResult();
            }

            public void changedUpdate(DocumentEvent e) {
                resetEmailResult();
            }
        });

        updateEmailButtons();
        passwordResult.setBounds(190, 160, 305, 25);
        add(passwordResult);
        confirmResult.setBounds(190, 220, 305, 25);
        add(confirmResult);
        idField.setBounds(190, 35, 190, 35);
        checkIdButton.setBounds(390, 35, 105, 35);
        add(checkIdButton);
        idResult.setBounds(190, 75, 305, 30);
        add(idResult);
        signupButton.setBounds(190, 610, 140, 40);
        add(signupButton);
        backButton.setBounds(345, 610, 150, 40);
        add(backButton);
        photoPreview.setBounds(550, 35, 200, 200);
        photoPreview.setBorder(BorderFactory.createLineBorder(java.awt.Color.LIGHT_GRAY));
        add(photoPreview);
        photoButton.setBounds(550, 250, 95, 35);
        photoButton.addActionListener(e -> choosePhoto());
        add(photoButton);
        removePhotoButton.setBounds(655, 250, 95, 35);
        removePhotoButton.addActionListener(e -> {
            profileImage = "";
            photoPreview.setIcon(null);
            photoPreview.setText("프로필 사진");
            removePhotoButton.setEnabled(false);
        });
        removePhotoButton.setEnabled(false);
        add(removePhotoButton);

        DocumentListener passwordListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updatePasswordResult(); }
            public void removeUpdate(DocumentEvent e) { updatePasswordResult(); }
            public void changedUpdate(DocumentEvent e) { updatePasswordResult(); }
        };
        passwordField.getDocument().addDocumentListener(passwordListener);
        confirmField.getDocument().addDocumentListener(passwordListener);

        idField.getDocument().addDocumentListener(new DocumentListener() {
            private void resetResult() {
                checkedLoginId = null;
                idResult.setText("아이디 중복확인 필요");
                checkIdButton.setEnabled(!signupPending);
                updateSignupButton();
            }
            public void insertUpdate(DocumentEvent e) { resetResult(); }
            public void removeUpdate(DocumentEvent e) { resetResult(); }
            public void changedUpdate(DocumentEvent e) { resetResult(); }
        });

        checkIdButton.addActionListener(e -> checkId());
        sendEmailCodeButton.addActionListener(e -> sendEmailCode());
        verifyEmailCodeButton.addActionListener(e -> verifyEmailCode());
        signupButton.addActionListener(e -> submitSignup());
        backButton.addActionListener(e -> {
            if (!signupPending) dispose();
        });
        Application.signupFrame = this;
        updateSignupButton();
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
        
        private void updateEmailButtons() {
            boolean available = !signupPending && !emailRequestPending;

            sendEmailCodeButton.setEnabled(
                    available && !emailField.getText().trim().isEmpty()
            );

            verifyEmailCodeButton.setEnabled(
                    available
                    && emailVerificationId > 0
                    && verifiedEmail == null
            );
        }

        private void sendEmailCode() {
            if (emailRequestPending || signupPending) {
                return;
            }

            String email = emailField.getText().trim();

            if (!serviceEmailFormat(email)) {
                emailResult.setText("이메일 형식을 확인하세요.");
                return;
            }

            verifiedEmail = null;
            emailVerificationId = 0;
            emailRequestPending = true;
            emailResult.setText("인증번호 발송 중...");
            updateEmailButtons();
            updateSignupButton();

            if (Application.sender == null
                    || !Application.sender.sendMessage(
                            EmailCodeRequest.send(email))) {

                emailRequestPending = false;
                emailResult.setText("발송 요청 실패. 서버 연결을 확인하세요.");
                updateEmailButtons();
            }
        }

        private void verifyEmailCode() {
            if (emailRequestPending || signupPending
                    || emailVerificationId <= 0) {
                return;
            }

            String code = emailCodeField.getText().trim();

            if (!code.matches("[0-9]{6}")) {
                emailResult.setText("인증번호 6자리를 입력하세요.");
                return;
            }

            emailRequestPending = true;
            emailResult.setText("인증번호 확인 중...");
            updateEmailButtons();

            if (Application.sender == null
                    || !Application.sender.sendMessage(
                            EmailCodeRequest.verify(
                                    emailVerificationId,
                                    emailField.getText().trim(),
                                    code))) {

                emailRequestPending = false;
                emailResult.setText("확인 요청 실패. 서버 연결을 확인하세요.");
                updateEmailButtons();
            }
        }

        public void showEmailCodeResult(String message) {
            if (!isDisplayable() || !emailRequestPending) {
                return;
            }

            String[] result = message.split(",", -1);

            if (result.length != 3) {
                emailRequestPending = false;
                emailResult.setText("인증 응답 형식 오류");
                updateEmailButtons();
                return;
            }

            String action = result[0];
            String status = result[2];

            emailRequestPending = false;

            if ("SEND".equals(action) && "SENT".equals(status)) {
                try {
                    emailVerificationId = Long.parseLong(result[1]);
                    emailResult.setText(
                            "이메일로 보낸 6자리 번호를 입력하세요."
                    );
                } catch (NumberFormatException e) {
                    emailVerificationId = 0;
                    emailResult.setText("인증 요청 번호 오류");
                }

            } else if ("VERIFY".equals(action)
                    && "VERIFIED".equals(status)
                    && result[1].equals(
                            Long.toString(emailVerificationId))) {

                verifiedEmail = emailField.getText().trim();
                emailResult.setText("이메일 인증 완료");

            } else if ("VERIFY".equals(action)) {
                verifiedEmail = null;
                emailResult.setText(
                        "인증번호가 틀렸거나 만료되었습니다."
                );
            } else {
                emailResult.setText(
                        "인증번호 발송 실패. 서버 설정과 메일 주소를 확인하세요."
                );
            }

            updateEmailButtons();
            updateSignupButton();
        }

        private boolean serviceEmailFormat(String email) {
            return email.length() <= 254
                    && email.matches(
                            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
                    );
        }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG / JPG", "png", "jpg", "jpeg"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path selected = chooser.getSelectedFile().toPath();
        imageLoading = true;
        photoButton.setEnabled(false);
        removePhotoButton.setEnabled(false);
        updateSignupButton();
        new SwingWorker<ProfileImages.PreparedPhoto, Void>() {
            protected ProfileImages.PreparedPhoto doInBackground() throws Exception {
                return ProfileImages.prepare(selected);
            }
            protected void done() {
                imageLoading = false;
                if (!isDisplayable()) return;
                try {
                    ProfileImages.PreparedPhoto photo = get();
                    profileImage = photo.encoded();
                    photoPreview.setText("");
                    photoPreview.setIcon(new ImageIcon(photo.preview()));
                } catch (Exception e) {
                    showError("사진을 읽을 수 없습니다. 5MB, 2천만 화소 이하의 PNG/JPG를 선택하세요.");
                }
                photoButton.setEnabled(!signupPending);
                removePhotoButton.setEnabled(!signupPending && !profileImage.isEmpty());
                updateSignupButton();
            }
        }.execute();
    }

    private void checkId() {
        String loginId = idField.getText().trim();
        checkedLoginId = null;
        updateSignupButton();
        if (!loginId.matches("[A-Za-z0-9_]{4,20}")) {
            idResult.setText("영문·숫자·_ 조합으로 4~20자 입력");
            return;
        }
        idResult.setText("확인 중...");
        checkIdButton.setEnabled(false);
        if (Application.sender == null
                || !Application.sender.sendMessage(new CheckIdRequest(loginId))) {
            checkIdButton.setEnabled(true);
            idResult.setText("전송 실패: 서버 연결을 확인하세요.");
        }
    }

    public void showCheckIdResult(String checkedId, String status) {
        if (!isDisplayable() || signupPending
                || !idField.getText().trim().equals(checkedId)) return;
        checkedLoginId = "AVAILABLE".equals(status) ? checkedId : null;
        checkIdButton.setEnabled(true);
        idResult.setText(switch (status) {
            case "AVAILABLE" -> "사용 가능한 아이디입니다.";
            case "DUPLICATE" -> "이미 사용 중인 아이디입니다.";
            default -> "확인에 실패했습니다. 다시 시도하세요.";
        });
        updateSignupButton();
    }

    private void updateSignupButton() {
        signupButton.setEnabled(
                !signupPending
                && !imageLoading
                && checkedLoginId != null
                && checkedLoginId.equals(idField.getText().trim())
                && verifiedEmail != null
                && verifiedEmail.equals(emailField.getText().trim())
        );
    }

    private void setSignupPending(boolean pending) {
        signupPending = pending;
        idField.setEditable(!pending);
        passwordField.setEditable(!pending);
        confirmField.setEditable(!pending);
        nicknameField.setEditable(!pending);
        postalCodeField.setEditable(!pending);
        addressField.setEditable(!pending);
        addressDetailField.setEditable(!pending);
        checkIdButton.setEnabled(!pending);
        backButton.setEnabled(!pending);
        photoButton.setEnabled(!pending && !imageLoading);
        removePhotoButton.setEnabled(!pending && !imageLoading && !profileImage.isEmpty());
        setDefaultCloseOperation(pending
                ? JFrame.DO_NOTHING_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        signupButton.setText(pending ? "가입 중..." : "가입");
        updateEmailButtons();
        updateSignupButton();
        emailField.setEditable(!pending);
        emailCodeField.setEditable(!pending);
    }

    private void submitSignup() {
        if (signupPending || imageLoading) return;
        String loginId = idField.getText().trim();
        if (checkedLoginId == null || !checkedLoginId.equals(loginId)) {
            showError("아이디 중복확인을 먼저 진행하세요.");
            return;
        }
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());
        String nickname = nicknameField.getText().trim();
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            showError("비밀번호는 공백 없이 8~20자이며, 영문·숫자·특수문자를 포함해야 합니다.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("비밀번호 확인 값이 일치하지 않습니다.");
            return;
        }
        if (!nickname.matches("[^,:\\p{Cntrl}]{1,50}")) {
            showError("닉네임은 1~50자로 입력하세요. 쉼표, 콜론, 제어 문자는 사용할 수 없습니다.");
            return;
        }
        String postalCode = postalCodeField.getText().trim();
        String address = addressField.getText().trim();
        String addressDetail = addressDetailField.getText().trim();
        boolean hasAddress = !postalCode.isEmpty() || !address.isEmpty() || !addressDetail.isEmpty();
        if (hasAddress && (!postalCode.matches("[0-9]{5}") || address.isEmpty())) {
            showError("주소를 입력하려면 우편번호 5자리와 기본주소를 함께 입력하세요.");
            return;
        }
        if (address.length() > 255 || addressDetail.length() > 255
                || Pattern.compile("\\p{Cntrl}").matcher(address + addressDetail).find()) {
            showError("주소는 각 255자 이내로, 제어 문자 없이 입력하세요.");
            return;
        }
        
        String email = emailField.getText().trim();
        String emailCode = emailCodeField.getText().trim();

        if (verifiedEmail == null
                || !verifiedEmail.equals(email)
                || emailVerificationId <= 0
                || !emailCode.matches("[0-9]{6}")) {
            showError("이메일 인증을 완료한 뒤 가입하세요.");
            return;
        }
        
        setSignupPending(true);
        if (Application.sender == null || !Application.sender.sendMessage(
        		new SignupRequest(
        		        loginId, password, nickname,
        		        postalCode, address, addressDetail,
        		        profileImage, email, emailVerificationId, emailCode))) {
            checkedLoginId = null;
            idResult.setText("아이디 중복확인 필요");
            setSignupPending(false);
            showError("요청 전송을 확인하지 못했습니다. 서버 연결과 아이디 중복 여부를 다시 확인하세요.");
        }
    }

    public void showSignupResult(String status) {
        if (!isDisplayable() || !signupPending) return;
        if ("SUCCESS".equals(status)) {
            JOptionPane.showMessageDialog(this, "회원가입이 완료되었습니다.");
            passwordField.setText("");
            confirmField.setText("");
            dispose();
            return;
        }
        if ("DUPLICATE_ID".equals(status)) {
            checkedLoginId = null;
            idResult.setText("이미 사용 중인 아이디입니다.");
        }
        setSignupPending(false);
        String message = switch (status) {
            case "DUPLICATE_ID" -> "이미 등록된 아이디입니다. 다른 아이디로 중복확인하세요.";
            case "INVALID_INPUT" -> "아이디, 비밀번호, 닉네임, 주소, 사진 입력값을 확인하세요.";
            default -> "회원가입 처리에 실패했습니다. 다시 시도하세요.";
            case "DUPLICATE_NICKNAME" -> "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력하세요.";
            case "INVALID_EMAIL_CODE" -> "이메일 인증번호가 틀렸거나 만료되었습니다. 다시 인증하세요.";
            case "DUPLICATE_EMAIL" -> "이미 등록된 이메일입니다. 다른 이메일을 사용하세요.";
        };
        showError(message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "회원가입 안내", JOptionPane.WARNING_MESSAGE);
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
        confirmResult.setText(confirm.isEmpty() ? ""
                : confirm.equals(password) ? "비밀번호가 일치합니다."
                : "비밀번호가 일치하지 않습니다.");
    }

    private void addRow(String text, JComponent field, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(45, y, 135, 35);
        add(label);
        field.setBounds(190, y, 305, 35);
        add(field);
    }
}

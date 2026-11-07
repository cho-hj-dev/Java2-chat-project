package view.frame;

import app.Application;
import dto.request.DTO;
import dto.request.ProfileRequest;
import dto.request.ProfileUpdateRequest;
import util.ProfileImages;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ProfileFrame extends JFrame {

    private final JTextField idField = new JTextField();
    private final JTextField nicknameField = new JTextField();
    private final JTextField postalCodeField = new JTextField();
    private final JTextField addressField = new JTextField();
    private final JTextField addressDetailField = new JTextField();

    private final JPasswordField currentPasswordField =
            new JPasswordField();
    private final JPasswordField newPasswordField =
            new JPasswordField();
    private final JPasswordField confirmPasswordField =
            new JPasswordField();

    private final JLabel photoLabel =
            new JLabel("프로필 사진 조회 전", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("프로필 조회 준비 중");

    private final JButton selectPhotoButton = new JButton("사진 변경");
    private final JButton removePhotoButton = new JButton("사진 제거");
    private final JButton saveButton = new JButton("저장");
    private final JButton reloadButton = new JButton("새로고침");
    private final JButton closeButton = new JButton("닫기");

    private String pendingRequestId;
    private String imageAction = "KEEP";
    private String imageData = "";
    private String savedNickname;

    private boolean loaded;
    private boolean saving;
    private boolean imageWorking;

    private final Timer timeout = new Timer(15000, e -> {
        boolean wasSaving = saving;
        loaded = false;
        finishRequest();

        statusLabel.setText(wasSaving
                ? "저장 결과를 확인하지 못했습니다. 새로고침으로 확인하세요."
                : "조회 시간이 초과됐습니다. 새로고침으로 다시 시도하세요.");
    });

    public ProfileFrame(JFrame owner) {
        setTitle("내 프로필");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(
                BorderFactory.createEmptyBorder(15, 20, 15, 20));
        setContentPane(content);

        JPanel photoPanel = new JPanel(new BorderLayout(10, 10));
        photoLabel.setPreferredSize(new Dimension(200, 200));
        photoLabel.setBorder(BorderFactory.createEtchedBorder());
        photoPanel.add(photoLabel, BorderLayout.CENTER);

        JPanel photoButtons = new JPanel(new GridLayout(2, 1, 0, 8));
        photoButtons.add(selectPhotoButton);
        photoButtons.add(removePhotoButton);
        photoPanel.add(photoButtons, BorderLayout.EAST);

        JPanel photoWrapper = new JPanel();
        photoWrapper.add(photoPanel);
        content.add(photoWrapper, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        idField.setEditable(false);

        addRow(form, "아이디", idField);
        addRow(form, "닉네임", nicknameField);
        addRow(form, "우편번호", postalCodeField);
        addRow(form, "기본주소", addressField);
        addRow(form, "상세주소", addressDetailField);
        addRow(form, "현재 비밀번호", currentPasswordField);
        addRow(form, "새 비밀번호", newPasswordField);
        addRow(form, "새 비밀번호 확인", confirmPasswordField);

        form.add(new JLabel(
                "비밀번호를 변경하지 않으면 비밀번호 세 칸을 비워두세요."));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.add(reloadButton);
        buttons.add(saveButton);
        buttons.add(closeButton);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        reloadButton.addActionListener(e -> loadProfile());
        saveButton.addActionListener(e -> saveProfile());
        selectPhotoButton.addActionListener(e -> choosePhoto());

        removePhotoButton.addActionListener(e -> {
            imageAction = "REMOVE";
            imageData = "";
            photoLabel.setIcon(null);
            photoLabel.setText("저장하면 사진이 제거됩니다.");
            statusLabel.setText("변경 내용을 적용하려면 저장을 누르세요.");
        });

        closeButton.addActionListener(e -> closeIfIdle());

        timeout.setRepeats(false);
        Application.profileFrame = this;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                timeout.stop();
                pendingRequestId = null;

                if (Application.profileFrame == ProfileFrame.this) {
                    Application.profileFrame = null;
                }
            }
        });

        setSize(650, 780);
        setLocationRelativeTo(owner);
        updateControls();

        SwingUtilities.invokeLater(this::loadProfile);
    }

    private void addRow(JPanel form, String title, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(125, 32));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        form.add(row);
    }

    private void closeIfIdle() {
        if (pendingRequestId == null && !imageWorking) {
            dispose();
        }
    }

    private void updateControls() {
        boolean busy = pendingRequestId != null || imageWorking;
        boolean editable = loaded && !busy;

        nicknameField.setEditable(editable);
        postalCodeField.setEditable(editable);
        addressField.setEditable(editable);
        addressDetailField.setEditable(editable);

        currentPasswordField.setEnabled(editable);
        newPasswordField.setEnabled(editable);
        confirmPasswordField.setEnabled(editable);

        selectPhotoButton.setEnabled(editable);
        removePhotoButton.setEnabled(editable);
        saveButton.setEnabled(editable);
        reloadButton.setEnabled(!busy);
        closeButton.setEnabled(!busy);

        saveButton.setText(saving ? "저장 중..." : "저장");
    }

    private void finishRequest() {
        timeout.stop();
        pendingRequestId = null;
        saving = false;
        updateControls();
    }

    private void sendRequest(String requestId, DTO request) {
        timeout.restart();
        updateControls();

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return Application.sender != null
                        && Application.sender.sendMessage(request);
            }

            @Override
            protected void done() {
                if (!requestId.equals(pendingRequestId)) {
                    return;
                }

                try {
                    if (!get()) {
                        requestFailed();
                    }
                } catch (Exception e) {
                    requestFailed();
                }
            }
        }.execute();
    }

    private void requestFailed() {
        loaded = false;
        finishRequest();
        statusLabel.setText(
                "전송 결과를 확인하지 못했습니다. 연결 확인 후 새로고침하세요.");
    }

    private void loadProfile() {
        if (!isDisplayable() || pendingRequestId != null || imageWorking) {
            return;
        }

        if (Application.me == null) {
            profileConnectionFailed();
            return;
        }

        loaded = false;
        saving = false;
        imageAction = "KEEP";
        imageData = "";

        photoLabel.setIcon(null);
        photoLabel.setText("사진 조회 중...");
        statusLabel.setText("DB에서 프로필을 조회하는 중입니다.");

        pendingRequestId = UUID.randomUUID().toString();
        sendRequest(pendingRequestId, new ProfileRequest(pendingRequestId));
    }

    public void profileConnectionFailed() {
        loaded = false;
        finishRequest();
        statusLabel.setText("서버 연결을 확인하고 다시 로그인하세요.");
    }

    private static String decodeText(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }

    public void showProfileResult(String message) {
        if (pendingRequestId == null || saving || !isDisplayable()) {
            return;
        }

        String[] parts = message.split(",", -1);

        if (!pendingRequestId.equals(parts[0])) {
            return;
        }

        if (parts.length != 9 || !"SUCCESS".equals(parts[1])) {
            loaded = false;
            finishRequest();
            statusLabel.setText("프로필 조회 실패. 연결과 로그인 상태를 확인하세요.");
            return;
        }

        try {
            String loginId = decodeText(parts[2]);
            String nickname = decodeText(parts[3]);
            String postalCode = decodeText(parts[4]);
            String address = decodeText(parts[5]);
            String detail = decodeText(parts[6]);

            if (Application.me == null
                    || !loginId.equals(Application.me.getId())) {
                throw new IllegalArgumentException("Account mismatch");
            }

            ImageIcon photo = null;

            if ("OK".equals(parts[7])) {
                photo = new ImageIcon(decodePhoto(parts[8]));
            } else if (!"NONE".equals(parts[7])
                    && !"MISSING".equals(parts[7])) {
                throw new IllegalArgumentException("Invalid image status");
            }

            idField.setText(loginId);
            nicknameField.setText(nickname);
            postalCodeField.setText(postalCode);
            addressField.setText(address);
            addressDetailField.setText(detail);

            clearPasswords();

            photoLabel.setIcon(photo);
            photoLabel.setText(photo != null ? ""
                    : "MISSING".equals(parts[7])
                    ? "사진 파일을 불러올 수 없습니다."
                    : "등록된 사진 없음");

            imageAction = "KEEP";
            imageData = "";
            loaded = true;
            finishRequest();

            statusLabel.setText("MISSING".equals(parts[7])
                    ? "정보 조회 완료. 서버의 사진 파일을 확인하세요."
                    : "조회 완료. 변경 후 저장을 누르세요.");

        } catch (Exception e) {
            loaded = false;
            finishRequest();
            statusLabel.setText("조회 결과를 표시하지 못했습니다. 다시 시도하세요.");
        }
    }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(
                new FileNameExtensionFilter("PNG / JPG 사진", "png", "jpg", "jpeg"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        var path = chooser.getSelectedFile().toPath();
        imageWorking = true;
        updateControls();
        statusLabel.setText("사진 처리 중...");

        new SwingWorker<ProfileImages.PreparedPhoto, Void>() {
            @Override
            protected ProfileImages.PreparedPhoto doInBackground()
                    throws Exception {
                return ProfileImages.prepare(path);
            }

            @Override
            protected void done() {
                imageWorking = false;

                if (!isDisplayable()) {
                    return;
                }

                try {
                    var photo = get();
                    imageData = photo.encoded();
                    imageAction = "REPLACE";
                    photoLabel.setIcon(new ImageIcon(photo.preview()));
                    photoLabel.setText("");
                    statusLabel.setText("사진 변경 준비 완료. 저장을 누르세요.");
                } catch (Exception e) {
                    showError("5MB 이하, 2천만 화소 이하의 PNG/JPG를 선택하세요.");
                }

                updateControls();
            }
        }.execute();
    }

    private static String passwordText(JPasswordField field) {
        char[] chars = field.getPassword();

        try {
            return new String(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private void saveProfile() {
        if (!loaded || pendingRequestId != null || imageWorking) {
            return;
        }

        String nickname = nicknameField.getText().trim();
        String postalCode = postalCodeField.getText().trim();
        String address = addressField.getText().trim();
        String detail = addressDetailField.getText().trim();

        if (!nickname.matches("[^,:|+\\p{Cntrl}]{1,50}")) {
            showError("닉네임은 1~50자이며 쉼표, :, |, +, 제어문자는 사용할 수 없습니다.");
            return;
        }

        boolean hasAddress =
                !postalCode.isEmpty() || !address.isEmpty() || !detail.isEmpty();

        if ((hasAddress
                && (!postalCode.matches("[0-9]{5}") || address.isEmpty()))
                || address.length() > 255 || detail.length() > 255
                || Pattern.compile("\\p{Cntrl}").matcher(address + detail).find()) {
            showError("우편번호 5자리와 기본주소를 함께 입력하세요. 주소는 각 255자 이하입니다.");
            return;
        }

        String currentPassword = passwordText(currentPasswordField);
        String newPassword = passwordText(newPasswordField);
        String confirmPassword = passwordText(confirmPasswordField);

        boolean changePassword = !currentPassword.isEmpty()
                || !newPassword.isEmpty() || !confirmPassword.isEmpty();

        if (changePassword) {
            if (currentPassword.isEmpty() || currentPassword.length() > 20) {
                showError("현재 비밀번호를 입력하세요.");
                return;
            }

            if (!newPassword.matches(
                    "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S{8,20}$")) {
                showError("새 비밀번호는 영문·숫자·특수문자를 포함한 8~20자이며 공백은 사용할 수 없습니다.");
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                showError("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                return;
            }
        }

        saving = true;
        savedNickname = nickname;
        pendingRequestId = UUID.randomUUID().toString();
        statusLabel.setText("프로필을 저장하는 중입니다.");

        ProfileUpdateRequest request = new ProfileUpdateRequest(
                pendingRequestId,
                nickname,
                postalCode,
                address,
                detail,
                imageAction,
                imageData,
                currentPassword,
                newPassword
        );

        sendRequest(pendingRequestId, request);
    }

    public void showUpdateResult(String message) {
        if (pendingRequestId == null || !saving || !isDisplayable()) {
            return;
        }

        String[] parts = message.split(",", -1);

        if (!pendingRequestId.equals(parts[0])) {
            return;
        }

        if (parts.length != 2) {
            requestFailed();
            return;
        }

        String status = parts[1];
        finishRequest();

        if ("SUCCESS".equals(status)) {
            clearPasswords();

            if (Application.me != null) {
                Application.me.setName(savedNickname);

                for (var user : Application.users) {
                    if (user.getId().equals(Application.me.getId())) {
                        user.setName(savedNickname);
                    }
                }

                if (LobbyFrame.chatRoomUserListPanel != null) {
                    LobbyFrame.chatRoomUserListPanel.paintChatUsers(
                            Application.users);
                }
            }

            loadProfile();
            JOptionPane.showMessageDialog(this, "프로필이 저장되었습니다.");
            return;
        }

        String error = switch (status) {
            case "DUPLICATE_NICKNAME" -> "이미 사용 중인 닉네임입니다.";
            case "WRONG_PASSWORD" -> "현재 비밀번호가 올바르지 않습니다.";
            case "INVALID_PASSWORD" -> "새 비밀번호 조건을 확인하세요.";
            case "INVALID_NICKNAME" -> "닉네임 입력값을 확인하세요.";
            case "INVALID_ADDRESS" -> "우편번호와 주소 입력값을 확인하세요.";
            case "INVALID_IMAGE" -> "사진을 다시 선택하세요.";
            case "UNAUTHORIZED" -> "다시 로그인하세요.";
            case "NOT_FOUND" -> "회원 정보를 찾을 수 없습니다.";
            case "INVALID_INPUT" -> "입력값을 확인하세요.";
            default -> "저장 결과를 확인하지 못했습니다. 새로고침으로 확인하세요.";
        };

        if ("ERROR".equals(status) || "UNAUTHORIZED".equals(status)
                || "NOT_FOUND".equals(status)) {
            loaded = false;
            updateControls();
        }

        statusLabel.setText(error);
        showError(error);
    }

    private void clearPasswords() {
        currentPasswordField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this, message, "프로필 안내", JOptionPane.WARNING_MESSAGE);
    }

    private static BufferedImage decodePhoto(String encoded)
            throws IOException {

        if (encoded.length() > 349528) {
            throw new IOException("Image too large");
        }

        byte[] bytes = Base64.getDecoder().decode(encoded);

        if (bytes.length > 262144) {
            throw new IOException("Image too large");
        }

        try (var input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {

            if (input == null) {
                throw new IOException("Invalid image");
            }

            var readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IOException("Invalid image");
            }

            var reader = readers.next();

            try {
                reader.setInput(input, true, true);

                if (!reader.getFormatName().equalsIgnoreCase("png")
                        || reader.getWidth(0) != 200
                        || reader.getHeight(0) != 200) {
                    throw new IOException("Expected 200x200 PNG");
                }

                BufferedImage image = reader.read(0);

                if (image == null) {
                    throw new IOException("Invalid image");
                }

                return image;
            } finally {
                reader.dispose();
            }
        }
    }
}
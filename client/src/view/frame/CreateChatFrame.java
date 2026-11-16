package view.frame;

import app.Application;
import domain.RoomType;
import domain.User;
import dto.request.CreateChatRoomRequest;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class CreateChatFrame extends JFrame {

    private final JComboBox<String> roomTypeBox =
            new JComboBox<>(new String[]{"그룹 채팅", "1:1 채팅"});
    private final JTextField chatNameField = new JTextField();
    private final JLabel targetLabel = new JLabel("대화 상대");
    private final JComboBox<UserChoice> targetBox = new JComboBox<>();
    private final JButton okButton = new JButton("확인");

    
    
    public CreateChatFrame() {
        setTitle("채팅방 생성");
        setLayout(null);
        setSize(600, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JLabel typeLabel = new JLabel("채팅방 종류");
        typeLabel.setBounds(100, 35, 100, 35);
        add(typeLabel);

        roomTypeBox.setBounds(200, 35, 300, 35);
        roomTypeBox.addActionListener(event -> updateForm());
        add(roomTypeBox);

        JLabel nameLabel = new JLabel("채팅방 이름");
        nameLabel.setBounds(100, 90, 100, 35);
        add(nameLabel);

        chatNameField.setBounds(200, 90, 300, 35);
        add(chatNameField);

        targetLabel.setBounds(100, 145, 100, 35);
        add(targetLabel);

        targetBox.setBounds(200, 145, 300, 35);
        targetBox.addActionListener(event -> updateForm());
        add(targetBox);

        okButton.setBounds(140, 235, 150, 40);
        okButton.addActionListener(event -> createRoom());
        add(okButton);

        JButton cancelButton = new JButton("취소");
        cancelButton.setBounds(310, 235, 150, 40);
        cancelButton.addActionListener(event -> setVisible(false));
        add(cancelButton);

        updateForm();
        setVisible(false);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            // 창을 열 때마다 현재 접속 중인 사용자를 다시 표시한다.
            refreshTargets();
        }
        super.setVisible(visible);
    }

    private RoomType selectedRoomType() {
        return roomTypeBox.getSelectedIndex() == 1
                ? RoomType.DIRECT
                : RoomType.GROUP;
    }

    private void updateForm() {
        boolean direct = selectedRoomType() == RoomType.DIRECT;

        targetLabel.setVisible(direct);
        targetBox.setVisible(direct);

        // 1:1 방은 상대가 한 명 이상 있을 때만 생성할 수 있다.
        okButton.setEnabled(
                !direct || targetBox.getSelectedItem() != null);
    }

    private void refreshTargets() {
        targetBox.removeAllItems();

        if (Application.me == null || Application.users == null) {
            updateForm();
            return;
        }

        for (User user : Application.users) {
            if (!user.getId().equals(Application.me.getId())) {
                targetBox.addItem(new UserChoice(user));
            }
        }

        updateForm();
    }

    private void createRoom() {
        if (Application.me == null) {
            JOptionPane.showMessageDialog(this, "로그인 후 이용하세요.");
            return;
        }

        String chatRoomName = chatNameField.getText().trim();

        // 서버와 같은 이름 규칙으로 먼저 검사한다.
        if (!chatRoomName.matches("[\\p{L}\\p{N}_ -]{1,40}")
                || "Lobby".equalsIgnoreCase(chatRoomName)) {
            JOptionPane.showMessageDialog(
                    this,
                    "채팅방 이름은 한글·영문·숫자·공백·_·-로 "
                    + "1~40자 입력하세요.");
            return;
        }

        // 이미 받은 방 목록에 같은 이름이 있는지 확인한다.
        boolean duplicate = Application.chatRooms.stream()
                .anyMatch(room -> room.getName().equals(chatRoomName));
        if (duplicate) {
            JOptionPane.showMessageDialog(
                    this, "이미 사용 중인 채팅방 이름입니다.");
            return;
        }

        RoomType roomType = selectedRoomType();
        String targetUserId = "";

        if (roomType == RoomType.DIRECT) {
            UserChoice selected =
                    (UserChoice) targetBox.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(
                        this, "1:1 대화 상대를 선택하세요.");
                return;
            }
            targetUserId = selected.user.getId();
        }

        // 서버가 방 종류와 선택한 상대를 확인하고 방을 생성한다.
        boolean sent = Application.sender.sendMessage(
                new CreateChatRoomRequest(
                        chatRoomName,
                        roomType,
                        Application.me.getId(),
                        targetUserId));

        if (!sent) {
            JOptionPane.showMessageDialog(
                    this, "채팅방 생성 요청을 보내지 못했습니다.");
            return;
        }

        setVisible(false);
        chatNameField.setText("");

        // 생성자는 방 화면을 열고, 상대는 로비 목록에서 방을 연다.
        ChatFrame chatFrame = new ChatFrame(chatRoomName, roomType);
        Application.chatPanelMap.put(
                chatRoomName, chatFrame.getChatPanel());
        Application.chatRoomUserListPanelMap.put(
                chatRoomName, chatFrame.getChatRoomUserListPanel());
    }

    private static class UserChoice {
        private final User user;

        UserChoice(User user) {
            this.user = user;
        }

        @Override
        public String toString() {
            return user.getName() + " (" + user.getId() + ")";
        }
    }
}
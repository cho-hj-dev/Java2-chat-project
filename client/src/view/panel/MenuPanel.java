package view.panel;

import domain.User;
import dto.request.InviteChatRequest;
import dto.request.AdminAccessRequest;
import java.util.ArrayList;
import java.util.List;

import app.Application;
import dto.request.ExitChatRequest;
import view.frame.LobbyFrame;
import view.frame.ProfileFrame;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import view.frame.NotepadFrame;

import view.frame.TicTacToeFrame;

import view.frame.CalendarFrame;

public class MenuPanel extends JPanel {

    String chatRoomName;

    JButton createChatBtn = new JButton("새로운 채팅방 생성");

    JButton exitBtn = new JButton("나가기");
    
    private final JButton inviteBtn = new JButton("사용자 초대");
    private final JButton profileBtn = new JButton("내 프로필");
    private ProfileFrame profileFrame;
    private final JButton toolsBtn = new JButton("도구");
    private final JPopupMenu toolsMenu = new JPopupMenu();
    private final JMenuItem adminItem = new JMenuItem("관리자 화면");
    private boolean exitPending = false;

    public MenuPanel(JFrame frame, String chatRoomName) {
        setLayout(null);

        this.chatRoomName = chatRoomName;

        createChatBtn.setBounds(15, 10, 175, 35);
        createChatBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("chat room create btn clicked");
                LobbyFrame.createChatFrame.setVisible(true);
            }
        });
        add(createChatBtn);
        
        JMenuItem notepadItem = new JMenuItem("메모장");
        notepadItem.addActionListener(event -> new NotepadFrame());
        toolsMenu.add(notepadItem);
        
        JMenuItem calendarItem = new JMenuItem("일정 관리");
        calendarItem.addActionListener(event -> {
            // 이미 열린 달력 창이 있으면 새로 만들지 않고 앞으로 가져온다.
            if (Application.calendarFrame == null
                    || !Application.calendarFrame.isDisplayable()) {
                Application.calendarFrame = new CalendarFrame();
            } else {
                Application.calendarFrame.toFront();
            }
        });
        toolsMenu.add(calendarItem);
        
        // 로비의 도구 메뉴에서 독립적인 틱택토 창을 연다.
        JMenuItem gameItem = new JMenuItem("틱택토");
        gameItem.addActionListener(event -> new TicTacToeFrame());
        toolsMenu.add(gameItem);

        adminItem.addActionListener(event -> {
            if (Application.sender == null
                    || !Application.sender.sendMessage(new AdminAccessRequest())) {
                JOptionPane.showMessageDialog(frame,
                        "관리자 권한 확인 요청을 보내지 못했습니다.");
            }
        });
        toolsMenu.add(adminItem);

        toolsBtn.setBounds(200, 10, 85, 35);
        toolsBtn.addActionListener(event -> {
            adminItem.setVisible(Application.me != null
                    && Application.me.isAdmin());
            // 하단 버튼이므로 메뉴를 버튼 위쪽으로 펼친다.
            toolsMenu.show(
                    toolsBtn, 0, -toolsMenu.getPreferredSize().height);
        });
        toolsBtn.setVisible(false);
        add(toolsBtn);
        
        createChatBtn.setVisible(false); // 로비 채팅방, 생성된 채팅방에 따라 버튼 show, hide

        exitBtn.setBounds(205, 10, 180, 35);
        exitBtn.addActionListener(event -> {
            if (exitPending) {
                return;
            }

            int answer = JOptionPane.showConfirmDialog(
                    frame,
                    "채팅방을 정말로 나가시겠습니까?",
                    "채팅방 나가기",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }

            // 서버의 성공 응답이 올 때까지 중복 요청을 막는다.
            exitPending = true;
            exitBtn.setEnabled(false);

            boolean sent = Application.sender.sendMessage(
                    new ExitChatRequest(chatRoomName, Application.me.getId()));

            if (!sent) {
                exitRequestFinished();
                JOptionPane.showMessageDialog(
                        frame, "나가기 요청을 보내지 못했습니다.");
            }
        });
        add(exitBtn);
        inviteBtn.setBounds(15, 10, 180, 35);
        inviteBtn.addActionListener(event -> inviteUser(frame));
        inviteBtn.setVisible(false); // 로비에는 초대 버튼을 표시하지 않는다.
        add(inviteBtn);
        
        createChatBtn.setVisible(false);
        
        profileBtn.setBounds(295, 10, 90, 35);
        profileBtn.setVisible(false);

        profileBtn.addActionListener(e -> {
            if (Application.me == null) {
                JOptionPane.showMessageDialog(frame, "로그인 후 이용하세요.");
                return;
            }

            if (profileFrame == null || !profileFrame.isDisplayable()) {
                profileFrame = new ProfileFrame(frame);
            }

            profileFrame.setVisible(true);
            profileFrame.toFront();
        });

        add(profileBtn);

        frame.add(this);

        setBounds(410, 460, 400, 50);
        setVisible(true);
    }

    public void setCreateChatBtnVisible(boolean bool) {
    	toolsBtn.setVisible(bool);
        createChatBtn.setVisible(bool);
        profileBtn.setVisible(bool);

        if (bool) {
            exitBtn.setVisible(false);
        }
    }

    public void setExitBtnVisible(boolean visible) {
        exitBtn.setVisible(visible);
    }

    public void setInviteBtnVisible(boolean visible) {
        // 그룹 방이면 초대·나가기 버튼을 나란히 보여준다.
        // 1:1 방이면 나가기 버튼만 넓게 보여준다.
        inviteBtn.setVisible(visible);
        exitBtn.setBounds(
                visible ? 205 : 15,
                10,
                visible ? 180 : 370,
                35);
    }
    
    private void inviteUser(JFrame frame) {
        ChatPanel chatPanel = Application.chatPanelMap.get(chatRoomName);
        if (chatPanel == null || Application.me == null) {
            return;
        }

        List<User> roomUsers = chatPanel.getRoomUsers();
        List<User> candidates = new ArrayList<>();

        // 로비의 접속자 중 자신과 현재 방 참여자를 제외한다.
        for (User onlineUser : Application.users) {
            if (onlineUser.getId().equals(Application.me.getId())) {
                continue;
            }

            boolean alreadyInRoom = roomUsers.stream()
                    .anyMatch(roomUser ->
                            roomUser.getId().equals(onlineUser.getId()));

            if (!alreadyInRoom) {
                candidates.add(onlineUser);
            }
        }

        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(
                    frame, "초대할 수 있는 접속 중인 사용자가 없습니다.");
            return;
        }

        String[] choices = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            User user = candidates.get(i);
            choices[i] = user.getName() + " (" + user.getId() + ")";
        }

        Object selected = JOptionPane.showInputDialog(
                frame,
                "초대할 사용자를 선택하세요.",
                "채팅방 초대",
                JOptionPane.QUESTION_MESSAGE,
                null,
                choices,
                choices[0]);

        if (selected == null) {
            return;
        }

        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equals(selected)) {
                boolean sent = Application.sender.sendMessage(
                        new InviteChatRequest(
                                chatRoomName,
                                candidates.get(i).getId()));

                if (!sent) {
                    JOptionPane.showMessageDialog(
                            frame, "초대 요청을 보내지 못했습니다.");
                }
                return;
            }
        }
    }
    
    public void exitRequestFinished() {
        exitPending = false;
        exitBtn.setEnabled(true);
    }
}

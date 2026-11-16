package view.frame;

import app.Application;
import domain.ChatRoom;
import domain.RoomType;

import view.panel.ChatPanel;
import view.panel.ChatRoomUserListPanel;
import view.panel.MenuPanel;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class ChatFrame extends JFrame implements WindowListener {

    String chatRoomName;

    ChatPanel chatPanel;

    ChatRoomUserListPanel chatRoomUserListPanel;

    MenuPanel menuPanel;

    public ChatFrame(String chatRoomName) {
        // 로비 목록에서 방을 열 때 저장된 종류를 찾아 사용한다.
        this(chatRoomName, findRoomType(chatRoomName));
    }

    public ChatFrame(String chatRoomName, RoomType roomType) {
        super(chatRoomName);

        setLayout(null);
        setSize(830, 550);

        this.chatRoomName = chatRoomName;
        chatPanel = new ChatPanel(this, chatRoomName);
        chatRoomUserListPanel = new ChatRoomUserListPanel(this);
        menuPanel = new MenuPanel(this, chatRoomName);
        menuPanel.setExitBtnVisible(true);
        menuPanel.setInviteBtnVisible(roomType == RoomType.GROUP);

        addWindowListener(this);
        setVisible(true);
    }

    private static RoomType findRoomType(String chatRoomName) {
        for (ChatRoom room : Application.chatRooms) {
            if (room.getName().equals(chatRoomName)) {
                return room.getType();
            }
        }

        // 종류를 확인할 수 없을 때는 초대 버튼을 숨긴다.
        return RoomType.DIRECT;
    }

    public ChatPanel getChatPanel() {
        return chatPanel;
    }

    public ChatRoomUserListPanel getChatRoomUserListPanel() { return chatRoomUserListPanel; }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {

    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }
}

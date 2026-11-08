package network;

import app.Application;
import domain.ChatRoom;
import dto.response.*;
import dto.type.DtoType;
import view.frame.LobbyFrame;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import view.frame.SignupFrame;

public class MessageReceiver extends Thread {

    Socket socket;

    public MessageReceiver(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String str;
            while ((str = reader.readLine()) != null) {
                String[] token = str.split(":", 2);
                if (token.length != 2) continue;
                DtoType type = DtoType.valueOf(token[0]);
                String message = token[1];
                // Preserve response order and update Swing components on the EDT.
                SwingUtilities.invokeLater(() -> {
                    try {
                        processReceivedMessage(type, message);
                    } catch (RuntimeException e) {
                        System.err.println("Invalid server response: " + type);
                        if (type == DtoType.LOGIN_RESULT && Application.loginFrame != null) {
                            Application.loginFrame.showLoginResult("ERROR", "", "");
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Connection ended: " + e.getClass().getSimpleName());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Socket close failed.");
            }
            SwingUtilities.invokeLater(() -> {
                if (Application.loginFrame != null) {
                    Application.loginFrame.showLoginResult("ERROR", "", "");
                }
                if (Application.profileFrame != null) {
                    Application.profileFrame.profileConnectionFailed();
                }
                if (Application.signupFrame != null) {
                    Application.signupFrame.showSignupResult("ERROR");
                }
            });
        }
    }

    private void processReceivedMessage(DtoType type, String message) {
    	if (Application.me == null && type != DtoType.LOGIN_RESULT
    	        && type != DtoType.CHECK_ID
    	        && type != DtoType.EMAIL_CODE_RESULT
    	        && type != DtoType.ACCOUNT_RECOVERY_RESULT) return;

        switch (type) {
        
	        case ACCOUNT_RECOVERY_RESULT: {
	            if (Application.findIdFrame != null) {
	                Application.findIdFrame.showResult(message);
	            }
	            if (Application.resetPasswordFrame != null) {
	                Application.resetPasswordFrame.showResult(message);
	            }
	            break;
	        }
	        
	        case EMAIL_CODE_RESULT: {
	            if (Application.signupFrame != null) {
	                Application.signupFrame.showEmailCodeResult(message);
	            }
	            break;
	        }
        	
	        case PROFILE_UPDATE_RESULT: {
	            if (Application.profileFrame != null) {
	                Application.profileFrame.showUpdateResult(message);
	            }
	            break;
	        }
	        
	        case PROFILE_RESULT: {
	            if (Application.profileFrame != null) {
	                Application.profileFrame.showProfileResult(message);
	            }
	            break;
	        }
	        
            case LOGIN_RESULT: {
                String[] result = message.split(",", -1);
                if (result.length != 3) throw new IllegalArgumentException("Invalid login result");
                String loginId = new String(Base64.getDecoder().decode(result[1]), StandardCharsets.UTF_8);
                String nickname = new String(Base64.getDecoder().decode(result[2]), StandardCharsets.UTF_8);
                if (Application.loginFrame != null) {
                    Application.loginFrame.showLoginResult(result[0], loginId, nickname);
                }
                break;
            }
            
            case CHECK_ID: {
                String[] result = message.split(",", 2);
                if (result.length == 2 && Application.signupFrame != null) {
                    Application.signupFrame.showCheckIdResult(result[0], result[1]);
                }
                break;
            }
            
            case SIGNUP: {
                SignupFrame frame = Application.signupFrame;
                if (frame != null) frame.showSignupResult(message);
                break;
            }
            
            case LOGIN:
                InitResponse initRes = new InitResponse(message);
                Application.chatRooms = initRes.getChatRooms();
                Application.users = initRes.getUsers();

                LobbyFrame.chatRoomUserListPanel.paintChatUsers(Application.users); // 전체 리스트
                LobbyFrame.chatRoomListPanel.paintChatRoomList();
                break;

            case MESSAGE:
                MessageResponse messageRes = new MessageResponse(message);
                Application.chatPanelMap.get(messageRes.getChatRoomName()).addMessage(messageRes.getMessageType(), messageRes.getUserName(), messageRes.getMessage());
                break;

            case CREATE_CHAT:
                CreateChatRoomResponse createChatRoomResponse = new CreateChatRoomResponse(message);
                String chatRoomName = createChatRoomResponse.getName();

                ChatRoom newChatRoom = new ChatRoom(chatRoomName);
                Application.chatRooms.add(newChatRoom);

                LobbyFrame.chatRoomListPanel.addChatRoomLabel(chatRoomName); // 새로 생성된 채팅방을 채팅방 목록에 추가
                break;

            case USER_LIST:
                UserListResponse userListRes = new UserListResponse(message);
                Application.chatRoomUserListPanelMap.get(userListRes.getChatRoomName()).paintChatUsers(userListRes.getUsers()); // 갱신된 채팅방 사용자 리스트 설정
                break;

            case CHAT_ROOM_LIST:
                ChatRoomListResponse chatRoomListRes = new ChatRoomListResponse(message);
                Application.chatRooms = chatRoomListRes.getChatRooms();
                LobbyFrame.chatRoomListPanel.paintChatRoomList();
                break;
        }
    }
}

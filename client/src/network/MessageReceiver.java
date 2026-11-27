package network;

import dto.request.EnterChatRequest;
import view.frame.ChatFrame;
import javax.swing.JOptionPane;

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
import view.panel.ChatPanel;
import view.style.EmoticonIcons;

import dto.request.WeatherRequest;

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
                
                // 다운로드 바이트는 화면 스레드에서 조립하지 않는다.
                if (type == DtoType.FILE_DOWNLOAD_BEGIN
                        || type == DtoType.FILE_DOWNLOAD_CHUNK
                        || type == DtoType.FILE_DOWNLOAD_END) {
                    FileDownloadClient.INSTANCE.onMessage(type, message);
                    continue;
                }
                
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
            
            FileDownloadClient.INSTANCE.connectionClosed();
            
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
                
                // 수신 연결이 끝나면 남아 있는 채팅 화면에서도 전송을 막는다.
                for (ChatPanel panel : Application.chatPanelMap.values()) {
                    panel.connectionClosed();
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
        
	        case FILE_NOTICE: {
	            // ID,Base64(방),Base64(파일명),MIME,크기,Base64(발신자)
	            String[] values = message.split(",", 6);
	            if (values.length != 6) {
	                break;
	            }
	
	            long attachmentId = Long.parseLong(values[0]);
	            String roomName = new String(
	                    Base64.getDecoder().decode(values[1]),
	                    StandardCharsets.UTF_8);
	            String fileName = new String(
	                    Base64.getDecoder().decode(values[2]),
	                    StandardCharsets.UTF_8);
	            int fileSize = Integer.parseInt(values[4]);
	            String senderName = new String(
	                    Base64.getDecoder().decode(values[5]),
	                    StandardCharsets.UTF_8);
	
	            ChatPanel panel = Application.chatPanelMap.get(roomName);
	            if (panel != null) {
	                panel.showFileNotice(
	                        attachmentId, senderName, fileName, fileSize);
	            }
	            break;
	        }
        
	        case FILE_READY: {
	            FileUploadClient.INSTANCE.onReady(message);
	            break;
	        }
	
	        case FILE_RESULT: {
	            FileUploadClient.INSTANCE.onResult(message);
	            break;
	        }
        
	        case EMOTICON_ASSET: {
	            // 서버 형식: ID,Base64(이름),MIME,Base64(이미지)
	            String[] values = message.split(",", 4);
	            if (values.length != 4) {
	                break;
	            }
	
	            String name = new String(
	                    Base64.getDecoder().decode(values[1]),
	                    StandardCharsets.UTF_8);
	            byte[] imageData = Base64.getDecoder().decode(values[3]);
	
	            EmoticonIcons.registerDbAsset(
	                    values[0], name, values[2], imageData);
	            break;
	        }
	
	        case EMOTICON_ASSET_END: {
	            if ("OK".equals(message)) {
	                // 로비를 포함해 이미 열려 있는 채팅 화면의 메뉴를 갱신한다.
	                for (ChatPanel panel : Application.chatPanelMap.values()) {
	                    panel.refreshEmoticonMenu();
	                }
	                System.out.println("DB 이모티콘 수신: "
	                        + EmoticonIcons.dbAssetCount() + "개");
	            }
	            break;
	        }
	        
	        case CHAT_SEARCH_ITEM: {
	            // 서버가 보낸 결과 한 건을 해당 채팅방의 검색 창에 추가한다.
	            ChatSearchItem item = ChatSearchItem.parse(message);
	            ChatPanel panel = Application.chatPanelMap.get(item.roomName());
	
	            if (panel != null) {
	                panel.addSearchResult(item);
	            }
	            break;
	        }
	
	        case CHAT_SEARCH_END: {
	            // 서버 형식: Base64(방 이름),OK 또는 ERROR,결과 수
	            String[] values = message.split(",", -1);
	            if (values.length != 3) {
	                break;
	            }
	
	            String roomName = new String(
	                    Base64.getDecoder().decode(values[0]),
	                    StandardCharsets.UTF_8);
	            boolean success = "OK".equals(values[1]);
	            int resultCount = Integer.parseInt(values[2]);
	
	            ChatPanel panel = Application.chatPanelMap.get(roomName);
	            if (panel != null) {
	                panel.finishSearch(success, resultCount);
	            }
	            break;
	        }
	        
            case CHAT_HISTORY_BEGIN: {
                String roomName = new String(
                        Base64.getDecoder().decode(message),
                        StandardCharsets.UTF_8);
                ChatPanel panel = Application.chatPanelMap.get(roomName);
                if (panel != null) {
                    panel.beginHistory();
                }
                break;
            }

            case CHAT_HISTORY_ITEM: {
                ChatHistoryItem item = ChatHistoryItem.parse(message);
                ChatPanel panel =
                        Application.chatPanelMap.get(item.roomName());
                if (panel != null) {
                    panel.addHistoryItem(item);
                }
                break;
            }

            case CHAT_HISTORY_END: {
                String[] values = message.split(",", 2);
                if (values.length != 2) {
                    break;
                }
                String roomName = new String(
                        Base64.getDecoder().decode(values[0]),
                        StandardCharsets.UTF_8);
                ChatPanel panel = Application.chatPanelMap.get(roomName);
                if (panel != null) {
                    panel.finishHistory("OK".equals(values[1]));
                }
                break;
            }

	        case EXIT_CHAT_RESULT: {
	            // 서버 형식: SUCCESS,방이름 또는 ERROR,방이름
	            String[] values = message.split(",", 2);
	            if (values.length != 2) {
	                break;
	            }
	
	            ChatPanel panel = Application.chatPanelMap.get(values[1]);
	            if (panel != null && panel.getFrame() instanceof ChatFrame chatFrame) {
	                chatFrame.showExitResult("SUCCESS".equals(values[0]));
	            }
	            break;
	        }
	        case SCHEDULE_RESULT: {
	            // 이 메서드는 이미 Swing 이벤트 스레드에서 호출된다.
	            if (Application.calendarFrame != null
	                    && Application.calendarFrame.isDisplayable()) {
	                Application.calendarFrame.showScheduleResult(message);
	            }
	            break;
	        }
        
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

                // 로그인 초기 데이터를 받은 뒤 서버에 날씨를 요청한다.
                if (!Application.sender.sendMessage(new WeatherRequest())) {
                    LobbyFrame.chatRoomUserListPanel.setWeatherText("날씨: 요청 실패");
                }
                break;
                
            case WEATHER_RESULT: {
                if ("ERROR".equals(message)) {
                    LobbyFrame.chatRoomUserListPanel.setWeatherText(
                            "날씨: 현재 조회할 수 없습니다.");
                    break;
                }

                try {
                    // 서버 응답: OK,Base64(지역),기온,Base64(설명)
                    String[] values = message.split(",", 4);
                    if (values.length != 4 || !"OK".equals(values[0])) {
                        throw new IllegalArgumentException("잘못된 날씨 응답");
                    }

                    String city = new String(
                            Base64.getDecoder().decode(values[1]),
                            StandardCharsets.UTF_8);
                    double temperature = Double.parseDouble(values[2]);
                    String description = new String(
                            Base64.getDecoder().decode(values[3]),
                            StandardCharsets.UTF_8);

                    String display = String.format(
                            java.util.Locale.KOREAN,
                            "현재 날씨 (%s): %.1f°C, %s",
                            city, temperature, description);
                    LobbyFrame.chatRoomUserListPanel.setWeatherText(display);
                } catch (RuntimeException exception) {
                    LobbyFrame.chatRoomUserListPanel.setWeatherText(
                            "날씨: 응답을 표시할 수 없습니다.");
                }
                break;
            }
            
            case INVITE_CHAT_RESULT: {
                if ("SENT".equals(message)) {
                    JOptionPane.showMessageDialog(
                            Application.lobbyFrame, "초대 알림을 보냈습니다.");
                } else {
                    JOptionPane.showMessageDialog(
                            Application.lobbyFrame,
                            "초대할 수 없습니다. 그룹 방과 참여자 상태를 확인해주세요.");
                }
                break;
            }

            case INVITE_CHAT: {
                // 서버 응답: Base64(방이름),Base64(초대한 사람 닉네임)
                String[] values = message.split(",", 2);
                if (values.length != 2) {
                    break;
                }

                String roomName = new String(
                        Base64.getDecoder().decode(values[0]),
                        StandardCharsets.UTF_8);
                String inviterName = new String(
                        Base64.getDecoder().decode(values[1]),
                        StandardCharsets.UTF_8);

                int answer = JOptionPane.showConfirmDialog(
                        Application.lobbyFrame,
                        inviterName + "님이 [" + roomName
                                + "] 채팅방에 초대했습니다.\n입장할까요?",
                        "채팅방 초대",
                        JOptionPane.YES_NO_OPTION);

                if (answer != JOptionPane.YES_OPTION) {
                    break;
                }

                // 이미 열려 있다면 중복 창을 만들지 않는다.
                if (Application.chatPanelMap.containsKey(roomName)) {
                    JOptionPane.showMessageDialog(
                            Application.lobbyFrame, "이미 열려 있는 채팅방입니다.");
                    break;
                }

                ChatFrame chatFrame = new ChatFrame(
                        roomName, domain.RoomType.GROUP);
                Application.chatPanelMap.put(
                        roomName, chatFrame.getChatPanel());
                Application.chatRoomUserListPanelMap.put(
                        roomName, chatFrame.getChatRoomUserListPanel());

                // 기존 채팅방 입장 요청을 재사용한다.
                boolean sent = Application.sender.sendMessage(
                        new EnterChatRequest(roomName, Application.me.getId()));

                if (!sent) {
                    Application.chatPanelMap.remove(roomName);
                    Application.chatRoomUserListPanelMap.remove(roomName);
                    chatFrame.dispose();
                    JOptionPane.showMessageDialog(
                            Application.lobbyFrame, "채팅방 입장 요청에 실패했습니다.");
                }
                break;
            }

            case MESSAGE: {
                MessageResponse messageRes = new MessageResponse(message);
                ChatPanel panel = Application.chatPanelMap.get(
                        messageRes.getChatRoomName());
                if (panel != null) {
                    panel.addMessage(
                            messageRes.getMessageType(),
                            messageRes.getUserName(),
                            messageRes.getMessage());
                }
                break;
            }

            case CREATE_CHAT:
                CreateChatRoomResponse createChatRoomResponse = new CreateChatRoomResponse(message);
                String chatRoomName = createChatRoomResponse.getName();

                // 알림에 포함된 방 종류를 클라이언트 목록에도 보관한다.
                ChatRoom newChatRoom = new ChatRoom(
                        chatRoomName, createChatRoomResponse.getType());
                Application.chatRooms.add(newChatRoom);

                LobbyFrame.chatRoomListPanel.addChatRoomLabel(chatRoomName); // 새로 생성된 채팅방을 채팅방 목록에 추가
                break;

            case USER_LIST: {
                UserListResponse userListRes = new UserListResponse(message);

                // 로비 사용자 목록은 1:1 대화 상대 선택에도 사용한다.
                if (Application.LOBBY_CHAT_NAME.equals(
                        userListRes.getChatRoomName())) {
                    Application.users = userListRes.getUsers();
                }

                Application.chatRoomUserListPanelMap
                        .get(userListRes.getChatRoomName())
                        .paintChatUsers(userListRes.getUsers());
	             // 귓속말 대상 선택에도 같은 방 참여자 목록을 사용한다.
	                ChatPanel panel = Application.chatPanelMap.get(
	                        userListRes.getChatRoomName());
	                if (panel != null) {
	                    panel.setRoomUsers(userListRes.getUsers());
	                }
                break;
            }

            case CHAT_ROOM_LIST:
                ChatRoomListResponse chatRoomListRes = new ChatRoomListResponse(message);
                Application.chatRooms = chatRoomListRes.getChatRooms();
                LobbyFrame.chatRoomListPanel.paintChatRoomList();
                break;
        }
    }
}

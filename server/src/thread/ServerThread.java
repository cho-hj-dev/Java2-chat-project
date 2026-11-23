package thread;

import app.Application;
import dao.ChatDao;
import domain.ChatRoom;
import domain.User;
import domain.Member;
import domain.RoomType;
import java.util.Optional;
import dto.request.*;
import dto.response.*;
import dto.type.DtoType;
import dto.type.MessageType;
import exception.ChatRoomExistException;
import exception.ChatRoomNotFoundException;
import exception.UserNotFoundException;
import service.ChatService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Set;

import security.PasswordHasher;
import storage.ProfileImageStore;
import java.awt.image.BufferedImage;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import dao.UserDao;
import java.sql.SQLException;

import db.DBConnection;
import service.SignupEmailService;
import java.sql.Connection;

import service.WeatherService;

import service.ScheduleService;

import dao.ChatMessageDao;
import dao.ChatHistoryDao;
import dao.ChatSearchDao;
import dao.EmoticonAssetDao;

public class ServerThread extends Thread {

    Socket socket; // 담당자 (not 문지기)

    ChatService chatService;
    private User loggedInUser;
    private final ChatMessageDao chatMessageDao = new ChatMessageDao();
    private final ChatHistoryDao chatHistoryDao = new ChatHistoryDao();
    private final ChatSearchDao chatSearchDao = new ChatSearchDao();
    private final EmoticonAssetDao emoticonAssetDao =
            new EmoticonAssetDao();
    
 // 클라이언트가 임의의 내용 대신 정해진 이모티콘 ID만 보내도록 제한한다.
    private static final Set<String> VALID_EMOTICON_IDS = Set.of(
            "SMILE", "LAUGH", "WINK", "SAD", "ANGRY", "HEART");
    
    //(7주차) 모든 접속 스레드가 같은 날씨 결과를 사용해야 10분 캐시가 작동한다.
    private static final WeatherService WEATHER_SERVICE =
            new WeatherService();

    public ServerThread(Socket socket, ChatService chatService) {
        this.socket = socket;
        this.chatService = chatService;
    }

    @Override
    public void run() {
        // Keep one reader so buffered messages are not discarded between requests.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String str;
            while ((str = reader.readLine()) != null) {
                String[] token = str.split(":", 2);
                if (token.length != 2) continue;
                DtoType type;
                try {
                    type = DtoType.valueOf(token[0]);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (loggedInUser == null && type != DtoType.LOGIN
                        && type != DtoType.SIGNUP 
                        && type != DtoType.CHECK_ID
                        && type != DtoType.PROFILE_GET
                        && type != DtoType.PROFILE_UPDATE
                        && type != DtoType.EMAIL_CODE
                        && type != DtoType.ACCOUNT_RECOVERY) { //가입 전에도 인증 메일을 요청해야 한다.
                    sendLoginResult(LoginResultResponse.Status.INVALID_CREDENTIALS, "", "");
                    continue;
                }
                processReceiveMessage(type, token[1]);
            }
        } catch (Exception e) {
            // Do not log request contents, which can contain credentials.
            System.err.println("Client connection ended: " + e.getClass().getSimpleName());
        } finally {
            synchronized (chatService) {
                if (loggedInUser != null) {
                    chatService.getChatRoom(ChatDao.LOBBY_CHAT_NAME).removeUser(loggedInUser);
                    chatService.getChatRooms().forEach(room -> room.removeUser(loggedInUser));
                    chatService.getUsers().remove(loggedInUser);
                    loggedInUser = null;
                }
            }
            Application.sockets.remove(socket);
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Socket close failed.");
            }
        }
    }

    private void sendLoginResult(LoginResultResponse.Status status,
                                 String loginId, String nickname) throws IOException {
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
        writer.println(new LoginResultResponse(status, loginId, nickname));
        if (writer.checkError()) throw new IOException("Login response send failed.");
    }

    private User authenticateLogin(String message) throws IOException {
        if (loggedInUser != null) {
            sendLoginResult(LoginResultResponse.Status.ALREADY_LOGGED_IN, "", "");
            return null;
        }
        Optional<Member> member;
        try {
            LoginRequest request = new LoginRequest(message);
            if (!request.getId().matches("[A-Za-z0-9_]{4,20}")
                    || request.getPassword().isEmpty()
                    || request.getPassword().length() > 20) {
                sendLoginResult(LoginResultResponse.Status.INVALID_CREDENTIALS, "", "");
                return null;
            }
            char[] password = request.getPassword().toCharArray();
            try {
                member = new UserDao().authenticate(request.getId(), password);
            } finally {
                Arrays.fill(password, '\0');
            }
        } catch (IllegalArgumentException e) {
            sendLoginResult(LoginResultResponse.Status.INVALID_CREDENTIALS, "", "");
            return null;
        } catch (SQLException | GeneralSecurityException e) {
            System.err.println("Login processing failed: " + e.getClass().getSimpleName());
            sendLoginResult(LoginResultResponse.Status.ERROR, "", "");
            return null;
        }
        if (member.isEmpty()) {
            sendLoginResult(LoginResultResponse.Status.INVALID_CREDENTIALS, "", "");
            return null;
        }
        // Check and register under the shared service lock to avoid duplicate logins.
        synchronized (chatService) {
            boolean alreadyOnline = chatService.getUsers().stream()
                    .anyMatch(user -> user.getId().equals(member.get().getLoginId()));
            if (alreadyOnline) {
                sendLoginResult(LoginResultResponse.Status.ALREADY_LOGGED_IN, "", "");
                return null;
            }
            loggedInUser = new User(socket, member.get());
            chatService.addUser(loggedInUser);
            chatService.enterLobby(loggedInUser);
            return loggedInUser;
        }
    }
    
    private SignupResponse.Status registerMember(String message) {
        String imagePath = null;
        boolean stored = false;

        try {
            SignupRequest request = new SignupRequest(message);
            String loginId = request.getLoginId();
            String password = request.getPassword();
            String nickname = request.getNickname().trim();
            String postalCode = request.getPostalCode().trim();
            String address = request.getAddress().trim();
            String addressDetail = request.getAddressDetail().trim();
            String email = SignupEmailService.normalizeEmail(request.getEmail());
            String emailCode = request.getEmailCode();
            long verificationId = request.getVerificationId();

            boolean hasAddress = !postalCode.isEmpty()
                    || !address.isEmpty() || !addressDetail.isEmpty();
            boolean validAddress = (!hasAddress
                    || (postalCode.matches("[0-9]{5}") && !address.isEmpty()))
                    && address.length() <= 255
                    && addressDetail.length() <= 255
                    && !java.util.regex.Pattern.compile("\\p{Cntrl}")
                            .matcher(address + addressDetail).find();

            boolean validId = loginId.matches("[A-Za-z0-9_]{4,20}");
            boolean validPassword = password.matches(
                    "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S{8,20}$");
            boolean validNickname =
                    nickname.matches("[^,:\\p{Cntrl}]{1,50}");

            if (!validId || !validPassword || !validNickname
                    || !validAddress || !SignupEmailService.validEmail(email)) {
                return SignupResponse.Status.INVALID_INPUT;
            }

            SignupEmailService emailService = new SignupEmailService();

            // 화면에서 이미 확인했더라도 가입 요청에서 다시 검증한다.
            // 잘못된 번호라면 기존 검증 메서드가 시도 횟수를 기록한다.
            if (!emailService.verifySignupCode(
                    verificationId, email, emailCode)) {
                return SignupResponse.Status.INVALID_EMAIL_CODE;
            }

            BufferedImage image =
                    ProfileImageStore.decode(request.getProfileImage());

            char[] passwordChars = password.toCharArray();
            String passwordHash;
            try {
                passwordHash = PasswordHasher.hash(passwordChars);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }

            imagePath = ProfileImageStore.save(image);

            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);

                try {
                    // 동시 요청이나 번호 재사용을 막기 위해 트랜잭션 안에서 재확인한다.
                    if (!emailService.consumeSignupCode(
                            connection, verificationId, email, emailCode)) {
                        connection.rollback();
                        return SignupResponse.Status.INVALID_EMAIL_CODE;
                    }

                    int inserted = new UserDao().insertVerified(
                            connection, loginId, passwordHash, nickname,
                            postalCode, address, addressDetail,
                            imagePath, email);

                    if (inserted != 1) {
                        connection.rollback();
                        return SignupResponse.Status.ERROR;
                    }

                    connection.commit();
                    stored = true;
                    return SignupResponse.Status.SUCCESS;

                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }

        } catch (IllegalArgumentException e) {
            return SignupResponse.Status.INVALID_INPUT;

        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                String detail = e.getMessage();
                if (detail != null && detail.contains("uq_users_nickname")) {
                    return SignupResponse.Status.DUPLICATE_NICKNAME;
                }
                if (detail != null && detail.contains("uq_users_email")) {
                    return SignupResponse.Status.DUPLICATE_EMAIL;
                }
                return SignupResponse.Status.DUPLICATE_ID;
            }
            System.err.println("회원가입 DB 오류 코드: " + e.getErrorCode());
            return SignupResponse.Status.ERROR;

        } catch (Exception e) {
            System.err.println(
                    "회원가입 처리 실패: " + e.getClass().getSimpleName());
            return SignupResponse.Status.ERROR;

        } finally {
            if (!stored && imagePath != null) {
                try {
                    ProfileImageStore.delete(imagePath);
                } catch (IOException e) {
                    System.err.println("실패한 가입의 사진 정리 필요: " + imagePath);
                }
            }
        }
    }

    private void processReceiveMessage(DtoType type, String message)
            throws UserNotFoundException, ChatRoomNotFoundException, ChatRoomExistException, IOException {
        switch (type) {
        
	        case CHAT_SEARCH: {
	            handleChatSearch(message);
	            break;
	        }
	        case SCHEDULE: {
	            // 사용자 ID는 요청 본문이 아니라 로그인한 소켓에서 가져온다.
	            String result = new ScheduleService().handle(
	                    loggedInUser.getMemberId(), message);
	
	            writeResponse(
	                    socket,
	                    DtoType.SCHEDULE_RESULT + ":" + result);
	            break;
	        }
	        
	        case WEATHER: {
	            // 로그인한 사용자만 날씨를 요청할 수 있다.
	            // 기존 run() 메서드의 로그인 검사도 이 요청에 적용된다.
	            try {
	                WeatherService.WeatherInfo weather =
	                        WEATHER_SERVICE.getCurrentWeather();
	
	                // 지역명과 설명은 쉼표 등이 들어갈 수 있어 Base64로 전송한다.
	                String city = java.util.Base64.getEncoder()
	                        .encodeToString(weather.city().getBytes(
	                                java.nio.charset.StandardCharsets.UTF_8));
	                String description = java.util.Base64.getEncoder()
	                        .encodeToString(weather.description().getBytes(
	                                java.nio.charset.StandardCharsets.UTF_8));
	
	                writeResponse(socket,
	                        "WEATHER_RESULT:OK,"
	                        + city + ","
	                        + weather.temperature() + ","
	                        + description);
	            } catch (InterruptedException exception) {
	                Thread.currentThread().interrupt();
	                writeResponse(socket, "WEATHER_RESULT:ERROR");
	            } catch (IOException | IllegalStateException exception) {
	                // API 키나 요청 URL은 출력하지 않고 오류 종류만 기록한다.
	                System.err.println("날씨 조회 실패: "
	                        + exception.getClass().getSimpleName());
	                writeResponse(socket, "WEATHER_RESULT:ERROR");
	            }
	            break;
	        }
        
	        case ACCOUNT_RECOVERY: {
	            handleAccountRecovery(message);
	            break;
	        }
        
	        case EMAIL_CODE: {
	            handleEmailCode(message);
	            break;
	        }
	        
        	case PROFILE_GET: {
        		sendProfile(message);
        		break;
        	}
        	
        	case PROFILE_UPDATE: {
        	    updateProfile(message);
        	    break;
        	}
        	
	        case CHECK_ID: {
	            String loginId = message.trim();
	            CheckIdResponse.Status status;
	
	            if (!loginId.matches("[A-Za-z0-9_]{4,20}")) { // id는 영문, 숫자, 밑줄만 허용(4~20)
	                status = CheckIdResponse.Status.ERROR;
	            } else {
	                try {
	                    boolean exists = new UserDao()
	                            .findByLoginId(loginId)
	                            .isPresent();
	
	                    status = exists
	                            ? CheckIdResponse.Status.DUPLICATE
	                            : CheckIdResponse.Status.AVAILABLE;
	                } catch (SQLException e) {
	                    e.printStackTrace();
	                    status = CheckIdResponse.Status.ERROR;
	                }
	            }
	
	            PrintWriter writer =
	                    new PrintWriter(socket.getOutputStream(), true);
	            writer.println(new CheckIdResponse(loginId, status));
	            break;
	        }
	        
        
	        case SIGNUP: {
	            SignupResponse.Status status = registerMember(message);
	            PrintWriter signupWriter =
	                    new PrintWriter(socket.getOutputStream(), true);
	            signupWriter.println(new SignupResponse(status));
	            break;
	        }
	        
            case LOGIN: {
                synchronized (chatService) {
                    User user = authenticateLogin(message);
                    if (user == null) {
                        break;
                    }

                    sendLoginResult(
                            LoginResultResponse.Status.SUCCESS,
                            user.getId(),
                            user.getName());
                    
                    // 클라이언트가 이전 대화를 그리기 전에 DB의 이모티콘 이미지를 보낸다.
                    sendEmoticonAssets();

                    // 다른 접속자에게 로비 입장을 알린다.
                    sendMessage(new MessageResponse(
                            MessageType.ENTER,
                            ChatDao.LOBBY_CHAT_NAME,
                            user.getName(),
                            user.getEnterString()));

                    // 로그인한 회원에게 DB 참여 관계에 맞는 방 목록을 보낸다.
                    List<ChatRoom> chatRooms = visibleRoomsFor(user);
                    List<User> users = chatService.getUsers();
                    sendMessage(new InitDataResponse(chatRooms, users));

                    sendMessage(new UserListResponse(
                            ChatDao.LOBBY_CHAT_NAME,
                            chatService.getUsers()));

                    // 로그인 직후 로비의 이전 대화를 보낸다.
                    sendRecentHistory(ChatDao.LOBBY_CHAT_NAME);
                }
                break;
            }

            case MESSAGE: {
                synchronized (chatService) {
                // 클라이언트가 보낸 채팅 또는 그림 메시지를 해석한다.
                MessageResponse received = new MessageResponse(message);
                ChatRoom targetRoom =
                        chatService.getChatRoom(received.getChatRoomName());

                // 로그인한 사용자가 실제로 이 방에 들어와 있는지 확인한다.
                if (loggedInUser == null || targetRoom == null
                        || !targetRoom.getUsers().contains(loggedInUser)) {
                    break;
                }
                
	             // 귓속말은 방 전체에 전송하지 않고 지정한 참여자에게만 보낸다.
	             // 요청 본문 형식: 수신자ID|Base64(메시지)
	             if (received.getMessageType() == MessageType.WHISPER) {
	                 String[] whisperParts = received.getMessage().split("\\|", 2);
	                 if (whisperParts.length != 2) {
	                     break;
	                 }
	
	                 String targetId = whisperParts[0];
	                 String whisperText;
	
	                 try {
	                     whisperText = new String(
	                             java.util.Base64.getDecoder().decode(whisperParts[1]),
	                             java.nio.charset.StandardCharsets.UTF_8);
	                 } catch (IllegalArgumentException exception) {
	                     break;
	                 }
	
	                 // 빈 메시지, 지나치게 긴 메시지, 줄바꿈이 포함된 요청은 거부한다.
	                 if (whisperText.isBlank()
	                         || whisperText.length() > 2000
	                         || whisperText.indexOf('\n') >= 0
	                         || whisperText.indexOf('\r') >= 0) {
	                     break;
	                 }
	
	                 User recipient = null;
	                 for (User roomUser : targetRoom.getUsers()) {
	                     if (roomUser.getId().equals(targetId)) {
	                         recipient = roomUser;
	                         break;
	                     }
	                 }
	
	                 // 수신자가 현재 이 방의 참여자인지 서버에서 다시 확인한다.
	                 if (recipient == null || recipient == loggedInUser) {
	                     break;
	                 }
	
	                 MessageResponse whisper = new MessageResponse(
	                         MessageType.WHISPER,
	                         targetRoom.getName(),
	                         loggedInUser.getName(),
	                         whisperText);
	                 
	                 // 수신자에게 전달하기 전에 귓속말과 수신자 ID를 저장한다.
	                 try {
	                     chatMessageDao.save(
	                             targetRoom.getName(),
	                             loggedInUser.getMemberId(),
	                             recipient.getMemberId(),
	                             MessageType.WHISPER,
	                             whisperText);
	                 } catch (SQLException exception) {
	                     System.err.println(
	                             "귓속말 저장 실패: "
	                             + exception.getClass().getSimpleName());
	                     break;
	                 }
	
	                 // 기존 sendMessage(outgoing)은 방 전체로 보내므로 여기서는 사용하지 않는다.
	                 writeResponse(recipient.getSocket(), whisper);
	                 break;
	             }
	             
	             // 이모티콘은 그림 데이터가 아닌 짧은 ID만 DB에 저장한다.
	             if (received.getMessageType() == MessageType.EMOTICON
	                     && !VALID_EMOTICON_IDS.contains(received.getMessage())) {
	                 System.err.println("지원하지 않는 이모티콘 ID를 거부했습니다.");
	                 break;
	             }

                // 이미지라면 크기와 PNG 형식을 검사한다.
                if (received.getMessageType() == MessageType.IMAGE
                        && !isValidDrawing(received.getMessage())) {
                    System.err.println("잘못된 그림 메시지를 거부했습니다.");
                    break;
                }
                
	             // 일반 채팅, 그림, 이모티콘을 DB에 저장한 뒤 방에 전달한다.
	             // 귓속말은 위 분기에서 별도로 처리했다.
	             if (received.getMessageType() != MessageType.CHAT
	                     && received.getMessageType() != MessageType.IMAGE
	                     && received.getMessageType() != MessageType.EMOTICON) {
	                 break;
	             }
		
	             try {
	            	    chatMessageDao.save(
	            	            targetRoom.getName(),
	            	            loggedInUser.getMemberId(),
	            	            null,
	            	            received.getMessageType(),
	            	            received.getMessage());
	             } catch (SQLException | IllegalArgumentException exception) {
	            	    System.err.println(
	            	            "메시지 저장 실패: "
	            	            + exception.getClass().getSimpleName());
	            	    break;
	            	}

                // 닉네임은 클라이언트가 적어 보낸 값 대신 서버의 로그인 정보로 정한다.
                // 따라서 다른 사용자의 이름을 임의로 적어 보낼 수 없다.
                MessageResponse outgoing = new MessageResponse(
                        received.getMessageType(),
                        received.getChatRoomName(),
                        loggedInUser.getName(),
                        received.getMessage()
                );
                sendMessage(outgoing);
                break;
                }
            }
                
            case CREATE_CHAT: {
                try {
                    CreateChatRoomRequest request =
                            new CreateChatRoomRequest(message);

                    // 생성자는 요청에 적힌 ID가 아니라 이 소켓에서 로그인한 사용자다.
                    if (!loggedInUser.getId().equals(request.getUserId())) {
                        break;
                    }

                    User target = null;
                    if (request.getRoomType() == RoomType.DIRECT) {
                        // 1:1 상대는 현재 접속 중인 사용자여야 한다.
                        target = chatService.getUser(request.getTargetUserId());
                    }

                    ChatRoom room = chatService.createChatRoom(
                            request.getName(),
                            request.getRoomType(),
                            loggedInUser,
                            target);

                    CreateChatRoomResponse created =
                            new CreateChatRoomResponse(room);

                    if (room.getType() == RoomType.DIRECT) {
                        // 1:1 방 이름은 생성자와 지정한 상대에게만 전달한다.
                        writeResponse(loggedInUser.getSocket(), created);
                        writeResponse(target.getSocket(), created);
                    } else {
                    	// 그룹방도 참여자에게만 목록을 보여준다.
                        // 다른 사용자는 초대를 수락한 뒤 목록에 추가된다.
                        writeResponse(loggedInUser.getSocket(), created);
                    }

                    // 생성자는 곧 방 화면을 열므로 참여자 목록을 보내준다.
                    // 상대는 아직 방 화면을 열지 않았으므로 여기서는 보내지 않는다.
                    writeResponse(socket, new UserListResponse(
                            room.getName(),
                            new java.util.ArrayList<>(room.getUsers())));

                } catch (IllegalArgumentException
                        | ChatRoomExistException exception) {
                    System.err.println(
                            "채팅방 생성 실패: "
                            + exception.getClass().getSimpleName());
                }
                break;
            }
            
            case INVITE_CHAT: {
                // 요청 형식: 채팅방이름,초대받을사용자ID
                String[] values = message.split(",", -1);
                if (values.length != 2) {
                    writeResponse(socket, "INVITE_CHAT_RESULT:INVALID");
                    break;
                }

                String roomName = values[0];
                String targetId = values[1];
                ChatRoom room = chatService.getChatRoom(roomName);
                User target = chatService.getUser(targetId);

                // 실제 방 참여자만 초대할 수 있다.
                // 1:1 방은 인원을 늘릴 수 없으므로 초대 대상에서 제외한다.
                if (room == null
                        || room.getType() != RoomType.GROUP
                        || !room.getUsers().contains(loggedInUser)
                        || target == null
                        || target == loggedInUser
                        || room.getUsers().contains(target)) {
                    writeResponse(socket, "INVITE_CHAT_RESULT:INVALID");
                    break;
                }

                // 방 이름과 초대한 사람의 닉네임을 안전하게 전송한다.
                String encodedRoom = java.util.Base64.getEncoder()
                        .encodeToString(roomName.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));
                String encodedInviter = java.util.Base64.getEncoder()
                        .encodeToString(loggedInUser.getName().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));

                writeResponse(target.getSocket(),
                        "INVITE_CHAT:" + encodedRoom + "," + encodedInviter);
                writeResponse(socket, "INVITE_CHAT_RESULT:SENT");
                break;
            }

            case ENTER_CHAT: {
                synchronized (chatService) {
                    EnterChatRequest request = new EnterChatRequest(message);
                    String roomName = request.getChatRoomName();

                    // 현재 소켓에서 로그인한 사용자와 요청 ID가 일치해야 한다.
                    if (!loggedInUser.getId().equals(request.getUserId())) {
                        break;
                    }

                    ChatRoom room = chatService.getChatRoom(roomName);
                    if (room == null) {
                        break;
                    }

                    // 재접속한 1:1 방은 저장된 참여자 ID로 검사한다.
                    if (room.getType() == RoomType.DIRECT
                            && !room.hasMember(loggedInUser.getMemberId())) {
                        break;
                    }

                    // 그룹방 초대 수락 시 참여 기록도 DB에 추가된다.
                    chatService.enterChatRoom(roomName, loggedInUser.getId());

                    // 실시간 입장 알림보다 이전 대화를 먼저 전송한다.
                    sendRecentHistory(roomName);

                    sendMessage(new MessageResponse(
                            MessageType.ENTER,
                            roomName,
                            loggedInUser.getName(),
                            loggedInUser.getEnterString()));

                    sendMessage(new UserListResponse(
                            roomName,
                            chatService.getChatRoomUsers(roomName)));

                    // 새로 참여한 방을 로비 목록에도 반영한다.
                    writeResponse(
                            socket,
                            new ChatRoomListResponse(
                                    visibleRoomsFor(loggedInUser)));
                }
                break;
            }

            case EXIT_CHAT: {
                ExitChatRequest request = new ExitChatRequest(message);

                // 요청자가 실제 로그인 사용자이고, 이 방의 참여자인지 확인한다.
                if (!loggedInUser.getId().equals(request.getUserId())) {
                    writeResponse(socket, "EXIT_CHAT_RESULT:ERROR," + request.getChatRoomName());
                    break;
                }

                ChatRoom room = chatService.getChatRoom(
                        request.getChatRoomName());

                if (room == null
                        || !room.hasMember(loggedInUser.getMemberId())
                        || !room.getUsers().contains(loggedInUser)) {
                    writeResponse(socket, "EXIT_CHAT_RESULT:ERROR," + request.getChatRoomName());
                    break;
                }

                String roomName = room.getName();

                try {
                    // ChatService가 DB 참여 기록을 먼저 삭제한다.
                    // 마지막 참여자라면 빈 방도 삭제한다.
                    User exitUser = chatService.exitChatRoom(
                            roomName, loggedInUser.getId());

                    // 요청자에게 DB 처리가 성공했음을 알린다.
                    writeResponse(
                            socket, "EXIT_CHAT_RESULT:SUCCESS," + roomName);

                    if (room.ieExistUser()) {
                        // 방에 남은 접속자에게 퇴장과 사용자 목록을 알린다.
                        sendMessage(new MessageResponse(
                                MessageType.EXIT,
                                roomName,
                                exitUser.getName(),
                                exitUser.getExitString()));

                        sendMessage(new UserListResponse(
                                roomName,
                                new java.util.ArrayList<>(room.getUsers())));
                    }

                    // 각 사용자의 DB 참여 관계에 맞는 방 목록을 다시 보낸다.
                    sendMessage(new ChatRoomListResponse(
                            chatService.getChatRooms()));

                } catch (UserNotFoundException
                        | ChatRoomNotFoundException
                        | IllegalStateException exception) {
                    System.err.println(
                            "채팅방 나가기 실패: "
                            + exception.getClass().getSimpleName());
                 // 여러 방을 동시에 열었을 때 어느 방의 실패인지 구별한다.
                    writeResponse(
                            socket,
                            "EXIT_CHAT_RESULT:ERROR," + request.getChatRoomName());
                }

                break;
            }
        }
    }
    
    private List<ChatRoom> visibleRoomsFor(User viewer) {
        List<ChatRoom> visible = new java.util.ArrayList<>();

        for (ChatRoom room : chatService.getChatRooms()) {
            // 그룹/1:1 모두 저장된 참여자에게만 표시한다.
            // 방에서 나가면 참여자 ID가 삭제되므로 재접속해도 보이지 않는다.
            if (room.hasMember(viewer.getMemberId())) {
                visible.add(room);
            }
        }

        return visible;
    }

    private void handleChatSearch(String message) {
        String roomName = "";

        try {
            // 방 이름과 검색어를 해석한다.
            // 요청에는 사용자 ID를 넣지 않는다.
            ChatSearchRequest request = new ChatSearchRequest(message);
            roomName = request.getRoomName();

            // 현재 로그인한 사용자가 접근할 수 있는 방인지 확인한다.
            // 로비는 로그인한 사용자에게 열려 있고,
            // 일반 채팅방은 DB에 기록된 참여자만 검색할 수 있다.
            boolean allowed;
            synchronized (chatService) {
                ChatRoom room = chatService.getChatRoom(roomName);
                allowed = room != null
                        && (ChatDao.LOBBY_CHAT_NAME.equals(roomName)
                                || room.hasMember(loggedInUser.getMemberId()));
            }

            if (!allowed) {
                writeResponse(socket,
                        new ChatSearchEndResponse(roomName, false, 0));
                return;
            }

            // 로그인한 소켓의 사용자 ID를 DAO에 전달한다.
            // DAO에서도 방 참여 여부와 귓속말 열람 권한을 다시 검사한다.
            List<ChatSearchDao.SearchResult> results =
                    chatSearchDao.search(
                            roomName,
                            loggedInUser.getMemberId(),
                            request.getKeyword());

            // 결과 여러 건과 완료 응답을 한 묶음으로 전송한다.
            synchronized (socket) {
                for (ChatSearchDao.SearchResult result : results) {
                    writeResponse(socket, new ChatSearchItemResponse(
                            roomName,
                            result.messageId(),
                            result.senderLoginId(),
                            result.senderName(),
                            result.messageType(),
                            result.content(),
                            result.sentAt()));
                }

                // 결과가 0건이어도 OK와 0을 보내 검색 완료를 알린다.
                writeResponse(socket,
                        new ChatSearchEndResponse(
                                roomName, true, results.size()));
            }

        } catch (IllegalArgumentException | SQLException exception) {
            // 검색어와 메시지 내용은 로그에 남기지 않는다.
            System.err.println("채팅 검색 실패: "
                    + exception.getClass().getSimpleName());

            writeResponse(socket,
                    new ChatSearchEndResponse(roomName, false, 0));
        }
    }
    
    private void sendRecentHistory(String roomName) {
        // 응답의 방 이름을 Base64로 인코딩해 구분자와 충돌하지 않게 한다.
        String encodedRoom = java.util.Base64.getEncoder()
                .encodeToString(roomName.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));

        List<ChatHistoryDao.StoredMessage> history = null;
        try {
            // DAO가 방 참여 여부와 귓속말 조회 권한을 검사한다.
            history = chatHistoryDao.findRecent(
                    roomName, loggedInUser.getMemberId());
        } catch (SQLException exception) {
            System.err.println(
                    "이전 대화 조회 실패: "
                    + exception.getClass().getSimpleName());
        }

        // 한 사용자에게 보내는 기록 묶음이 다른 응답과 섞이지 않게 한다.
        synchronized (socket) {
            writeResponse(socket, "CHAT_HISTORY_BEGIN:" + encodedRoom);

            if (history != null) {
                for (ChatHistoryDao.StoredMessage item : history) {
                    writeResponse(socket, new ChatHistoryItemResponse(
                            roomName,
                            item.messageId(),
                            item.senderLoginId(),
                            item.senderName(),
                            item.recipientUserId(),
                            item.type(),
                            item.content(),
                            item.sentAt()));
                }
            }

            // 조회 실패와 기록 0건을 구별할 수 있게 상태를 보낸다.
            writeResponse(
                    socket,
                    "CHAT_HISTORY_END:" + encodedRoom + ","
                            + (history == null ? "ERROR" : "OK"));
        }
    }
    
    private void sendEmoticonAssets() {
        try {
            // DB에 등록된 각 PNG/GIF 이미지를 로그인한 사용자에게 보낸다.
            for (EmoticonAssetDao.EmoticonAsset asset
                    : emoticonAssetDao.findAll()) {
                writeResponse(socket, new EmoticonAssetResponse(asset));
            }

            // 이미지가 0개여도 완료 응답을 보내 목록 수신이 끝났음을 알린다.
            writeResponse(socket, "EMOTICON_ASSET_END:OK");

        } catch (SQLException exception) {
            // 이미지 조회 실패가 로그인 자체를 종료하지 않도록 분리한다.
            System.err.println("이모티콘 이미지 조회 실패: "
                    + exception.getClass().getSimpleName());
            writeResponse(socket, "EMOTICON_ASSET_END:ERROR");
        }
    }

    private void writeResponse(Socket target, Object response) {
        synchronized (target) {
            try {
                PrintWriter writer =
                        new PrintWriter(target.getOutputStream(), true);

                writer.println(response);

                if (writer.checkError()) {
                    System.err.println("응답 전송 실패");
                }
            } catch (IOException e) {
                System.err.println("연결이 종료된 사용자에게 전송하지 못했습니다.");
            }
        }
    }

    private void sendMessage(DTO dto) {
        switch (dto.getType()) {
            case LOGIN:
                writeResponse(socket, dto);
                break;

            case MESSAGE: {
                MessageResponse response = (MessageResponse) dto;
                ChatRoom room = chatService.getChatRoom(
                        response.getChatRoomName()
                );

                if (room == null) {
                    break;
                }

                for (User user :
                        new java.util.ArrayList<>(room.getUsers())) {

                    if (user.getSocket() != socket) {
                        writeResponse(user.getSocket(), dto);
                    }
                }
                break;
            }

            case USER_LIST: {
                UserListResponse response = (UserListResponse) dto;

                for (User user :
                        new java.util.ArrayList<>(response.getUsers())) {

                    writeResponse(user.getSocket(), dto);
                }
                break;
            }

            case CREATE_CHAT:
                // 그룹 채팅방 생성 알림에만 사용한다.
                for (Socket target :
                        new java.util.ArrayList<>(Application.sockets)) {
                    writeResponse(target, dto);
                }
                break;

            case CHAT_ROOM_LIST:
                // 각 사용자에게 자신이 볼 수 있는 방 목록만 전송한다.
                for (User viewer :
                        new java.util.ArrayList<>(chatService.getUsers())) {
                    writeResponse(
                            viewer.getSocket(),
                            new ChatRoomListResponse(visibleRoomsFor(viewer)));
                }
                break;
        }
    }
    
    private void sendProfile(String requestId) throws IOException {
        // 요청에는 회원 번호가 아닌 요청 식별용 UUID만 허용
        try {
            String normalizedId =
                    java.util.UUID.fromString(requestId).toString();

            if (!normalizedId.equals(requestId)) {
                return;
            }
        } catch (IllegalArgumentException e) {
            return;
        }

        ProfileResponse response;

        if (loggedInUser == null) {
            response = new ProfileResponse(
                    requestId,
                    "UNAUTHORIZED",
                    null,
                    "NONE",
                    ""
            );
        } else {
            try {
                // 클라이언트가 지정한 회원이 아닌,
                // 이 연결에서 로그인한 회원의 정보를 조회
                var found = new UserDao()
                        .findProfileById(loggedInUser.getMemberId());

                if (found.isEmpty()) {
                    response = new ProfileResponse(
                            requestId,
                            "NOT_FOUND",
                            null,
                            "NONE",
                            ""
                    );
                } else {
                    var profile = found.get();

                    String image = "";
                    String imageStatus = "NONE";

                    if (profile.imagePath() != null
                            && !profile.imagePath().isBlank()) {
                        try {
                            image = ProfileImageStore.readEncoded(
                                    profile.imagePath()
                            );
                            imageStatus = "OK";
                        } catch (IOException e) {
                            imageStatus = "MISSING";

                            System.err.println(
                                    "프로필 사진 파일을 불러오지 못했습니다."
                            );
                        }
                    }

                    response = new ProfileResponse(
                            requestId,
                            "SUCCESS",
                            profile,
                            imageStatus,
                            image
                    );
                }
            } catch (SQLException e) {
                System.err.println(
                        "프로필 조회 DB 오류 코드: " + e.getErrorCode()
                );

                response = new ProfileResponse(
                        requestId,
                        "ERROR",
                        null,
                        "NONE",
                        ""
                );
            }
        }

        synchronized (socket) {
            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            writer.println(response);

            if (writer.checkError()) {
                throw new IOException("프로필 조회 응답 전송 실패");
            }
        }
    }
    
    private static String decodeProfileValue(String value) {
        return new String(
                java.util.Base64.getDecoder().decode(value),
                java.nio.charset.StandardCharsets.UTF_8
        );
    }

    private void updateProfile(String message) throws IOException {
        String[] values = message.split(",", -1);

        // 요청 UUID 검증
        String requestId = values[0];

        try {
            if (!java.util.UUID.fromString(requestId)
                    .toString().equals(requestId)) {
                return;
            }
        } catch (IllegalArgumentException e) {
            return;
        }

        String status;
        String updatedNickname = null;

        if (loggedInUser == null) {
            status = "UNAUTHORIZED";
        } else if (values.length != 9 || message.length() > 400_000) {
            status = "INVALID_INPUT";
        } else {
            try {
                String nickname = decodeProfileValue(values[1]);
                String postalCode = decodeProfileValue(values[2]);
                String address = decodeProfileValue(values[3]);
                String addressDetail = decodeProfileValue(values[4]);

                String imageAction = values[5];
                String imageData = values[6];

                String currentPassword = decodeProfileValue(values[7]);
                String newPassword = decodeProfileValue(values[8]);

                status = new dao.ProfileDao().update(
                        loggedInUser.getMemberId(),
                        nickname,
                        postalCode,
                        address,
                        addressDetail,
                        imageAction,
                        imageData,
                        currentPassword,
                        newPassword
                );

                if ("SUCCESS".equals(status)) {
                    updatedNickname = nickname.trim();

                    synchronized (chatService) {
                        loggedInUser.setName(updatedNickname);
                    }
                }
            } catch (IllegalArgumentException e) {
                status = "INVALID_INPUT";
            } catch (SQLException | GeneralSecurityException
                    | IOException e) {
                // 비밀번호가 포함된 요청 내용은 출력하지 않음
                System.err.println(
                        "프로필 저장 실패: " + e.getClass().getSimpleName()
                );
                status = "ERROR";
            }
        }

        synchronized (socket) {
            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            writer.println(
                    DtoType.PROFILE_UPDATE_RESULT
                    + ":" + requestId + "," + status
            );

            if (writer.checkError()) {
                throw new IOException("프로필 저장 결과 전송 실패");
            }
        }

        if ("SUCCESS".equals(status)) {
            // 로비와 채팅방 사용자 목록의 닉네임 갱신
            sendMessage(new UserListResponse(
                    ChatDao.LOBBY_CHAT_NAME,
                    new java.util.ArrayList<>(chatService.getUsers())
            ));

            for (ChatRoom room :
                    new java.util.ArrayList<>(chatService.getChatRooms())) {

                sendMessage(new UserListResponse(
                        room.getName(),
                        new java.util.ArrayList<>(room.getUsers())
                ));
            }
        }
    }
    
    // 요청 형식은 발송 SEND, 이메일 / 확인 VERIFY, 요청번호, 이메일, 6자리번호, 이메일은 Base64로 전송
    private void handleEmailCode(String message) throws IOException {
        String[] values = message.split(",", -1);

        String action = values.length > 0 ? values[0] : "";
        String requestId = "0";
        String status = "ERROR";

        try {
            service.SignupEmailService emailService =
                    new service.SignupEmailService();

            if ("SEND".equals(action) && values.length == 2) {
                String email = new String(
                        java.util.Base64.getDecoder().decode(values[1]),
                        java.nio.charset.StandardCharsets.UTF_8
                );

                if (!service.SignupEmailService.validEmail(
                        service.SignupEmailService.normalizeEmail(email))) {
                    status = "INVALID_EMAIL";
                } else {
                    long id = emailService.sendSignupCode(email);
                    requestId = Long.toString(id);
                    status = "SENT";
                }

            } else if ("VERIFY".equals(action) && values.length == 4) {
                long id = Long.parseLong(values[1]);
                requestId = Long.toString(id);

                String email = new String(
                        java.util.Base64.getDecoder().decode(values[2]),
                        java.nio.charset.StandardCharsets.UTF_8
                );

                boolean matched = emailService.verifySignupCode(
                        id, email, values[3]
                );

                status = matched ? "VERIFIED" : "INVALID_CODE";
            }
        } catch (IllegalArgumentException e) {
            status = "INVALID_INPUT";
        } catch (Exception e) {
            // 인증번호와 메일 비밀번호는 로그에 남기지 않음
            System.err.println(
                    "이메일 인증 처리 실패: "
                    + e.getClass().getSimpleName()
            );
            status = "ERROR";
        }

        synchronized (socket) {
            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            writer.println(
                    DtoType.EMAIL_CODE_RESULT + ":"
                    + action + "," + requestId + "," + status
            );

            if (writer.checkError()) {
                throw new IOException("이메일 인증 응답 전송 실패");
            }
        }
    }
    
    private void handleAccountRecovery(String message) throws IOException {
        String[] values = message.split(",", -1);
        String action = values.length > 0 ? values[0] : "";
        String status = "ERROR";
        String requestId = "0";
        String encodedLoginId = "";

        try {
            service.AccountRecoveryService recovery =
                    new service.AccountRecoveryService();

            if ("FIND_ID_SEND".equals(action) && values.length == 2) {
                String email = new String(
                        java.util.Base64.getDecoder().decode(values[1]),
                        java.nio.charset.StandardCharsets.UTF_8);

                if (!service.SignupEmailService.validEmail(
                        service.SignupEmailService.normalizeEmail(email))) {
                    status = "INVALID_EMAIL";
                } else {
                    long id = recovery.sendFindIdCode(email);
                    requestId = Long.toString(id);
                    status = "SENT";
                }

            } else if ("FIND_ID_VERIFY".equals(action)
                    && values.length == 4) {
                long id = Long.parseLong(values[1]);
                requestId = Long.toString(id);

                String email = new String(
                        java.util.Base64.getDecoder().decode(values[2]),
                        java.nio.charset.StandardCharsets.UTF_8);

                String loginId = recovery.verifyFindIdCode(
                        id, email, values[3]);

                if (loginId == null) {
                    status = "INVALID_CODE";
                } else if (loginId.isEmpty()) {
                    status = "NO_ACCOUNT";
                } else {
                    status = "VERIFIED";
                    encodedLoginId = java.util.Base64.getEncoder()
                            .encodeToString(loginId.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8));
                }
            } else if ("RESET_SEND".equals(action)
                    && values.length == 3) {
                String loginId = new String(
                        java.util.Base64.getDecoder().decode(values[1]),
                        java.nio.charset.StandardCharsets.UTF_8);
                String email = new String(
                        java.util.Base64.getDecoder().decode(values[2]),
                        java.nio.charset.StandardCharsets.UTF_8);

                long id = recovery.sendResetCode(loginId, email);
                requestId = Long.toString(id);
                status = "SENT";

            } else if ("RESET_COMMIT".equals(action)
                    && values.length == 6) {
                long id = Long.parseLong(values[1]);
                requestId = Long.toString(id);

                String loginId = new String(
                        java.util.Base64.getDecoder().decode(values[2]),
                        java.nio.charset.StandardCharsets.UTF_8);
                String email = new String(
                        java.util.Base64.getDecoder().decode(values[3]),
                        java.nio.charset.StandardCharsets.UTF_8);
                String newPassword = new String(
                        java.util.Base64.getDecoder().decode(values[5]),
                        java.nio.charset.StandardCharsets.UTF_8);

                status = recovery.resetPassword(
                        id, loginId, email, values[4], newPassword);
            } else {
                status = "INVALID_INPUT";
            }

        } catch (IllegalArgumentException e) {
            status = "INVALID_INPUT";
        } catch (IllegalStateException e) {
            status = "RATE_LIMIT".equals(e.getMessage())
                    ? "WAIT" : "ERROR";
        } catch (Exception e) {
            System.err.println(
                    "아이디 찾기 처리 실패: "
                    + e.getClass().getSimpleName());
            status = "ERROR";
        }

        synchronized (socket) {
            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);
            writer.println(DtoType.ACCOUNT_RECOVERY_RESULT
                    + ":" + action + "," + status + ","
                    + requestId + "," + encodedLoginId);

            if (writer.checkError()) {
                throw new IOException("아이디 찾기 결과 전송 실패");
            }
        }
    }
    
    private static boolean isValidDrawing(String encoded) {
        // Base64 문자열 자체가 지나치게 크면 디코딩 전에 거부한다.
        if (encoded == null || encoded.length() > 350000) {
            return false;
        }

        try {
            byte[] png = java.util.Base64.getDecoder().decode(encoded);

            // 그림판의 600×350 PNG만 받는다. PNG 최대 크기는 256 KiB다.
            if (png.length < 24 || png.length > 256 * 1024) {
                return false;
            }

            // PNG 파일 시작 부분의 고정된 서명을 확인한다.
            int[] signature = {137, 80, 78, 71, 13, 10, 26, 10};
            for (int i = 0; i < signature.length; i++) {
                if ((png[i] & 0xff) != signature[i]) {
                    return false;
                }
            }

            // PNG 헤더에는 가로·세로 크기가 각각 4바이트로 기록된다.
            int width = ((png[16] & 0xff) << 24)
                    | ((png[17] & 0xff) << 16)
                    | ((png[18] & 0xff) << 8)
                    | (png[19] & 0xff);
            int height = ((png[20] & 0xff) << 24)
                    | ((png[21] & 0xff) << 16)
                    | ((png[22] & 0xff) << 8)
                    | (png[23] & 0xff);

            return width == 600 && height == 350;
        } catch (IllegalArgumentException exception) {
            // Base64 형식이 아니면 그림 메시지로 취급하지 않는다.
            return false;
        }
    }  
}

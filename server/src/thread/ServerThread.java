package thread;

import app.Application;
import dao.ChatDao;
import domain.ChatRoom;
import domain.User;
import domain.Member;
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

import security.PasswordHasher;
import storage.ProfileImageStore;
import java.awt.image.BufferedImage;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import dao.UserDao;
import java.sql.SQLException;

public class ServerThread extends Thread {

    Socket socket; // 담당자 (not 문지기)

    ChatService chatService;
    private User loggedInUser;

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
                        && type != DtoType.PROFILE_UPDATE) {
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
            boolean hasAddress = !postalCode.isEmpty() || !address.isEmpty() || !addressDetail.isEmpty();
            boolean validAddress = (!hasAddress || (postalCode.matches("[0-9]{5}") && !address.isEmpty()))
                    && address.length() <= 255 && addressDetail.length() <= 255
                    && !java.util.regex.Pattern.compile("\\p{Cntrl}")
                            .matcher(address + addressDetail).find();

            boolean validId = loginId.matches("[A-Za-z0-9_]{4,20}");
            boolean validPassword = password.matches(
                    "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S{8,20}$");
            boolean validNickname =
                    nickname.matches("[^,:\\p{Cntrl}]{1,50}");

            if (!validId || !validPassword || !validNickname || !validAddress) {
                return SignupResponse.Status.INVALID_INPUT;
            }

            BufferedImage image = ProfileImageStore.decode(request.getProfileImage());
            char[] passwordChars = password.toCharArray();
            String passwordHash;
            try {
                passwordHash = PasswordHasher.hash(passwordChars);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }

            imagePath = ProfileImageStore.save(image);
            int inserted = new UserDao().insert(
                    loginId, passwordHash, nickname, postalCode, address, addressDetail, imagePath);
            stored = inserted == 1;

            return inserted == 1
                    ? SignupResponse.Status.SUCCESS
                    : SignupResponse.Status.ERROR;

        } catch (IllegalArgumentException e) {
            return SignupResponse.Status.INVALID_INPUT;
        } catch (SQLException e) {
        	if (e.getErrorCode() == 1062) {
        	    String detail = e.getMessage();

        	    if (detail != null && detail.contains("uq_users_nickname")) {
        	        return SignupResponse.Status.DUPLICATE_NICKNAME;
        	    }

        	    return SignupResponse.Status.DUPLICATE_ID;
        	}
            System.err.println("회원가입 DB 오류 코드: " + e.getErrorCode());
            return SignupResponse.Status.ERROR;
        } catch (GeneralSecurityException e) {
            System.err.println("회원가입 비밀번호 해시 처리 실패");
            return SignupResponse.Status.ERROR;
        } catch (IOException e) {
            System.err.println("프로필 사진 저장 실패");
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
	        
            case LOGIN:
                User user = authenticateLogin(message);
                if (user == null) break;
                sendLoginResult(LoginResultResponse.Status.SUCCESS, user.getId(), user.getName());

                // [to 채팅방에 있는 다른 사용자] 입장 메시지 전송
                MessageResponse lobbyEnterMessageRes = new MessageResponse(MessageType.ENTER, ChatDao.LOBBY_CHAT_NAME, user.getName(), user.getEnterString());
                sendMessage(lobbyEnterMessageRes);


                // [to 로그인 사용자] 초기 데이터 전송 (전체 사용자 리스트, 채팅방 리스트)
                List<ChatRoom> chatRooms = chatService.getChatRooms();
                List<User> users = chatService.getUsers();

                InitDataResponse initRes = new InitDataResponse(chatRooms, users);
                sendMessage(initRes);

                // [to 채팅방에 있는 다른 사용자] 사용자 리스트 전송
                UserListResponse userListRes = new UserListResponse(ChatDao.LOBBY_CHAT_NAME, chatService.getUsers());
                sendMessage(userListRes);
                break;

            case MESSAGE:
                // [to 채팅방에 있는 다른 사용자] 메시지 전송
                MessageResponse messageResponse = new MessageResponse(message);
                sendMessage(messageResponse);
                break;

            case CREATE_CHAT:
                CreateChatRoomRequest createChatRoomReq = new CreateChatRoomRequest(message);

                ChatRoom chatRoom = chatService.createChatRoom(createChatRoomReq.getName(), createChatRoomReq.getUserId());
                chatService.enterChatRoom(chatRoom.getName(), createChatRoomReq.getUserId());

                // 새로 생성된 방 정보 전송
                CreateChatRoomResponse createChatRoomRes = new CreateChatRoomResponse(chatRoom);
                sendMessage(createChatRoomRes);

                // [to 채팅방에 있는 모든 사용자 (나 자신 포함)] 사용자 리스트 전송
                UserListResponse chatRoomUserListRes = new UserListResponse(chatRoom.getName(), chatService.getChatRoomUsers(chatRoom.getName()));
                sendMessage(chatRoomUserListRes);
                break;

            case ENTER_CHAT:
                // 서버에 채팅방에 들어온 사용자 설정
                EnterChatRequest enterChatReq = new EnterChatRequest(message);
                String enterChatRoomName = enterChatReq.getChatRoomName();
                String userId = enterChatReq.getUserId();
                chatService.enterChatRoom(enterChatRoomName, userId);

                // [to 채팅방에 있는 다른 사용자] 입장 메시지 전송
                User enterUser = chatService.getUser(userId);
                MessageResponse enterChatRoomEnterMessageRes = new MessageResponse(MessageType.ENTER, enterChatRoomName, enterUser.getName(), enterUser.getEnterString());
                sendMessage(enterChatRoomEnterMessageRes);

                // [to 채팅방에 있는 모든 사용자 (나 자신 포함)] 사용자 리스트 전송
                UserListResponse enterChatRoomUserListRes = new UserListResponse(enterChatRoomName, chatService.getChatRoomUsers(enterChatRoomName));
                sendMessage(enterChatRoomUserListRes);

                break;
            case EXIT_CHAT:
                ExitChatRequest exitChatReq = new ExitChatRequest(message);
                ChatRoom exitChatRoom = chatService.getChatRoom(exitChatReq.getChatRoomName());
                String exitChatRoomName = exitChatRoom.getName();
                User exitUser = chatService.exitChatRoom(exitChatReq.getChatRoomName(), exitChatReq.getUserId());

                if (exitChatRoom.ieExistUser()) {
                    // [to 채팅방에 있는 다른 사용자] 퇴장 메시지 전송
                    MessageResponse chatRoomExitMessageRes = new MessageResponse(MessageType.EXIT, exitChatReq.getChatRoomName(), exitUser.getName(), exitUser.getExitString());
                    sendMessage(chatRoomExitMessageRes);

                    // [to 채팅방에 있는 모든 사용자 (나 자신 포함)] 사용자 리스트 전송
                    UserListResponse exitChatRoomUserListRes = new UserListResponse(exitChatRoomName, chatService.getChatRoomUsers(exitChatRoomName));
                    sendMessage(exitChatRoomUserListRes);
                }

                // 채팅방에 더 이상 사용자가 없는 경우
                // 채팅방 목록 갱신
                else {
                    ChatRoomListResponse chatRoomListRes = new ChatRoomListResponse(chatService.getChatRooms());
                    sendMessage(chatRoomListRes);
                }

                break;
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
            case CHAT_ROOM_LIST:
                for (Socket target :
                        new java.util.ArrayList<>(Application.sockets)) {

                    writeResponse(target, dto);
                }
                break;

            default:
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
}

package service;

import app.Application;
import dao.ChatDao;
import dao.ChatPersistenceDao;
import domain.ChatRoom;
import domain.RoomType;
import domain.User;
import exception.ChatRoomExistException;
import exception.ChatRoomNotFoundException;
import exception.UserNotFoundException;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ChatService {

    private final ChatDao chatDao;
    private final ChatPersistenceDao persistenceDao;

    public ChatService(ChatDao chatDao) {
        this.chatDao = chatDao;
        this.persistenceDao = new ChatPersistenceDao();

        // 서버가 시작될 때 DB에 남아 있는 일반 채팅방을 복원한다.
        // Lobby는 ChatDao에서 따로 관리하므로 여기서는 제외한다.
        try {
            for (ChatPersistenceDao.StoredRoom stored
                    : persistenceDao.findRooms()) {

                ChatRoom room = new ChatRoom(
                        stored.roomId(),
                        stored.name(),
                        stored.type());

                for (int memberId
                        : persistenceDao.findMemberIds(stored.roomId())) {
                    room.addMemberId(memberId);
                }

                chatDao.addChatRoom(room);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "저장된 채팅방을 불러오지 못했습니다.", exception);
        }
    }

    public void addUser(User user) {
        chatDao.addUser(user);
    }

    public void enterLobby(User user) {
        chatDao.getLobby().addUser(user);
    }

    public synchronized void enterChatRoom(String chatRoomName, String userId)
            throws UserNotFoundException, ChatRoomNotFoundException {

        User user = chatDao.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        ChatRoom room = chatDao.findChatRoomByName(chatRoomName)
                .orElseThrow(
                        () -> new ChatRoomNotFoundException(chatRoomName));

        int memberId = user.getMemberId();

        // 1:1 방은 DB에 저장된 두 참여자만 다시 들어갈 수 있다.
        if (room.getType() == RoomType.DIRECT
                && !room.hasMember(memberId)) {
            throw new IllegalArgumentException(
                    "이 1:1 채팅방의 참여자가 아닙니다.");
        }

        // 그룹방은 공개 목록에서 선택해 입장할 수 있다.
        // 처음 입장하는 회원만 DB 참여자로 기록한다.
        if (!room.hasMember(memberId)) {
            try {
                persistenceDao.addMember(room.getRoomId(), memberId);
                room.addMemberId(memberId);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "채팅방 참여자를 저장하지 못했습니다.", exception);
            }
        }

        room.addUser(user);
    }

    // 기존 호출 방식이 있다면 그룹방 생성으로 연결한다.
    public ChatRoom createChatRoom(String chatRoomName, String userId)
            throws ChatRoomExistException {

        User creator = chatDao.findUserById(userId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "접속 중인 사용자를 찾을 수 없습니다."));

        return createChatRoom(
                chatRoomName, RoomType.GROUP, creator, null);
    }

    public synchronized ChatRoom createChatRoom(
            String name, RoomType type, User creator, User target)
            throws ChatRoomExistException {

        if (creator == null || type == null) {
            throw new IllegalArgumentException(
                    "생성자 또는 방 종류가 없습니다.");
        }

        // 기존 줄 단위 통신 형식을 깨뜨리는 문자를 허용하지 않는다.
        if (name == null
                || !name.matches("[\\p{L}\\p{N}_ -]{1,40}")
                || "Lobby".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException(
                    "사용할 수 없는 채팅방 이름입니다.");
        }

        boolean duplicate = chatDao.getChatRooms().stream()
                .anyMatch(room -> room.getName().equals(name));

        if (duplicate) {
            throw new ChatRoomExistException(name);
        }

        if (type == RoomType.DIRECT
                && (target == null || target == creator)) {
            throw new IllegalArgumentException(
                    "1:1 채팅 상대를 선택해야 합니다.");
        }

        try {
            // 방과 참여자 등록은 DAO 안에서 한 트랜잭션으로 처리한다.
            long roomId = persistenceDao.createRoom(
                    name,
                    type,
                    creator.getMemberId(),
                    target == null ? null : target.getMemberId());

            ChatRoom room = new ChatRoom(roomId, name, type);
            room.addMemberId(creator.getMemberId());
            room.addUser(creator);

            if (type == RoomType.DIRECT) {
                room.addMemberId(target.getMemberId());

                // 현재 생성 직후의 사용자 목록 동작을 유지한다.
                // 재접속한 사용자의 목록 처리는 다음 ServerThread 단계에서 바꾼다.
                room.addUser(target);
            }

            chatDao.addChatRoom(room);
            return room;

        } catch (SQLException exception) {
            // DB의 UNIQUE 제약도 중복 방 이름을 막는다.
            if (exception.getErrorCode() == 1062) {
                throw new ChatRoomExistException(name);
            }
            throw new IllegalStateException(
                    "채팅방을 DB에 저장하지 못했습니다.", exception);
        }
    }

    public synchronized User exitChatRoom(String chatRoomName, String userId)
            throws UserNotFoundException, ChatRoomNotFoundException {

        ChatRoom room = chatDao.findChatRoomByName(chatRoomName)
                .orElseThrow(
                        () -> new ChatRoomNotFoundException(chatRoomName));

        User user = chatDao.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!room.hasMember(user.getMemberId())) {
            throw new UserNotFoundException(userId);
        }

        try {
            // DB 삭제가 성공한 뒤 메모리 목록을 갱신한다.
            boolean removed = persistenceDao.leaveRoom(
                    room.getRoomId(), user.getMemberId());

            if (!removed) {
                throw new UserNotFoundException(userId);
            }

            room.removeMemberId(user.getMemberId());
            room.removeUser(user);

            // 다른 회원이 남았다면, 현재 접속자가 없어도 방을 유지한다.
            if (room.getMemberCount() == 0) {
                chatDao.getChatRooms().remove(room);
            }

            return user;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "채팅방 나가기를 DB에 반영하지 못했습니다.", exception);
        }
    }

    public List<User> getUsers() {
        return chatDao.getUsers();
    }

    public List<ChatRoom> getChatRooms() {
        return chatDao.getChatRooms();
    }

    public User getUser(String userId) {
        Optional<User> found = chatDao.findUserById(userId);

        if (found.isEmpty()) {
            System.out.println(
                    "[" + userId + "] 아이디의 사용자 없음");
            return null;
        }

        return found.get();
    }

    public ChatRoom getChatRoom(String chatRoomName) {
        if (ChatDao.LOBBY_CHAT_NAME.equals(chatRoomName)) {
            return chatDao.getLobby();
        }

        Optional<ChatRoom> found =
                chatDao.findChatRoomByName(chatRoomName);

        if (found.isEmpty()) {
            System.out.println(
                    "[" + chatRoomName + "] 이름의 채팅방 없음");
            return null;
        }

        return found.get();
    }

    public List<User> getChatRoomUsers(String chatRoomName) {
        ChatRoom room = getChatRoom(chatRoomName);
        return room == null ? null : room.getUsers();
    }

    public void disconnect(String userId)
            throws UserNotFoundException, IOException {

        User user = chatDao.getUser(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 접속 종료는 방 탈퇴가 아니다.
        // 화면에 연결된 사용자만 제거하고 DB 참여 기록은 유지한다.
        for (ChatRoom room : chatDao.getChatRooms()) {
            room.removeUser(user);
        }

        chatDao.getUsers().remove(user);

        List<Socket> sockets = Application.sockets;
        Socket clientSocket = user.getSocket();
        clientSocket.close();
        sockets.remove(clientSocket);
    }
}
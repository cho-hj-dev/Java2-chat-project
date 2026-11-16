package dto.response;

import domain.ChatRoom;
import domain.RoomType;
import domain.User;

import java.util.ArrayList;
import java.util.List;

public class InitResponse {
    private final List<ChatRoom> chatRooms = new ArrayList<>();
    private final List<User> users = new ArrayList<>();

    public InitResponse(String message) {
        // 서버 형식: 방목록+사용자목록
        String[] data = message.split("\\+", 2);
        if (data.length != 2) {
            throw new IllegalArgumentException("잘못된 로그인 초기 데이터");
        }

        if (!data[0].isEmpty()) {
            // 방 항목: 방이름,방종류
            for (String roomValue : data[0].split("\\|")) {
                String[] values = roomValue.split(",", 2);
                if (values.length != 2) {
                    throw new IllegalArgumentException("잘못된 방 정보");
                }

                chatRooms.add(new ChatRoom(
                        values[0],
                        RoomType.valueOf(values[1])));
            }
        }

        if (!data[1].isEmpty()) {
            // 사용자 항목: ID,닉네임
            for (String userValue : data[1].split("\\|")) {
                String[] values = userValue.split(",", 2);
                if (values.length != 2) {
                    throw new IllegalArgumentException("잘못된 사용자 정보");
                }

                users.add(new User(values[0], values[1]));
            }
        }
    }

    public List<ChatRoom> getChatRooms() {
        return chatRooms;
    }

    public List<User> getUsers() {
        return users;
    }
}
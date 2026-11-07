package domain;


import java.net.Socket;
import java.util.Date;

public class User {

    private Socket socket; // 현재 입장한 채팅방의 소켓

    private String id; // 아이디: 사용자 식별자

    private String name; // 이름: 채팅방에서 사용되는 이름

    private Date createdAt; // 로그인 시점
    
    private int memberId;

    public User(Socket socket, Member member) {
        this.socket = socket;
        this.memberId = member.getUserId();
        this.id = member.getLoginId();
        this.name = member.getNickname();
        this.createdAt = new Date();
    }



    
    public int getMemberId() {
        return memberId;
    }
    
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getEnterString() {
        return "[" + name + "]님이 입장했습니다.";
    }

    public String getExitString() {
        return "[" + name + "]님이 퇴장했습니다.";
    }
    
    public void setName(String name) {
        this.name = name;
    }
}

package domain;

import java.util.Date;

public class User {

    private String id; // 아이디: 사용자 식별자

    private String name; // 이름: 채팅방에서 사용되는 이름
    private final String userRole;

    private Date createdAt; // 로그인 시점

    public User(String id, String name) {
        this(id, name, new Date(), "USER");
    }

    private User(String id, String name, Date createdAt, String userRole) {
        this.id = id;
        this.name = name;
        this.userRole = userRole;
        this.createdAt = createdAt;
    }

    public User(String id, String name, String date) {
        this(id, name, new Date(date), "USER");
    }

    public static User loggedIn(String id, String name, String userRole) {
        return new User(id, name, new Date(), userRole);
    }

    public String getId() {
        return id;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return name;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(userRole);
    }
    
    public void setName(String name) {
        this.name = name;
    }
}

package dto.type;

public enum DtoType {
	PROFILE_GET, // 내 프로필 조회 요청
	PROFILE_RESULT, // 프로필 조회 결과 응답
	
	PROFILE_UPDATE,
	PROFILE_UPDATE_RESULT,
	
    INIT,

    LOGIN,
    LOGIN_RESULT,
    CHECK_ID,
    SIGNUP,
    CREATE_CHAT,
    ENTER_CHAT, EXIT_CHAT,
    MESSAGE,

    USER_LIST, CHAT_ROOM_LIST,
}

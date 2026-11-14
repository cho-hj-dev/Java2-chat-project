package dto.type;

public enum DtoType {
	WEATHER,         // 현재 날씨 요청
	WEATHER_RESULT,  // 현재 날씨 조회 결과
	
	ACCOUNT_RECOVERY,
	ACCOUNT_RECOVERY_RESULT,
	
	EMAIL_CODE,
	EMAIL_CODE_RESULT,
	
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

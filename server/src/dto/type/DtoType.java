package dto.type;

public enum DtoType {
	FILE_GET,            // 클라이언트 → 서버: 저장된 파일 요청
	FILE_DOWNLOAD_BEGIN, // 서버 → 클라이언트: 파일 정보
	FILE_DOWNLOAD_CHUNK, // 서버 → 클라이언트: 파일 조각
	FILE_DOWNLOAD_END,   // 서버 → 클라이언트: 전송 완료 또는 오류
	
	FILE_NOTICE,  // 서버 → 같은 방 사용자: 새 파일이 저장됨
	
	FILE_START,   // 클라이언트 → 서버: 파일명·크기·해시 전달
	FILE_CHUNK,   // 클라이언트 → 서버: 파일 조각 전달
	FILE_END,     // 클라이언트 → 서버: 업로드 완료
	FILE_READY,   // 서버 → 클라이언트: 업로드 시작 허용 여부
	FILE_RESULT,  // 서버 → 클라이언트: DB 저장 결과
	
	EMOTICON_ASSET,      // 서버 → 클라이언트: 이모티콘 이미지 한 개
	EMOTICON_ASSET_END,  // 서버 → 클라이언트: 이미지 목록 전송 완료
	
	CHAT_SEARCH,       // 클라이언트 → 서버: 채팅 기록 검색
	CHAT_SEARCH_ITEM,  // 서버 → 클라이언트: 검색 결과 한 건
	CHAT_SEARCH_END,   // 서버 → 클라이언트: 검색 완료 또는 실패
	
	CHAT_HISTORY_BEGIN,
	CHAT_HISTORY_ITEM,
	CHAT_HISTORY_END,
	
	SCHEDULE,         // 클라이언트 → 서버: 일정 CRUD 요청
	SCHEDULE_RESULT,  // 서버 → 클라이언트: 일정 처리 결과
	
	INVITE_CHAT,         // 클라이언트 → 서버: 그룹 방 초대 요청
	INVITE_CHAT_RESULT,  // 서버 → 요청자: 초대 요청 결과
	
	WEATHER,         // 클라이언트 → 서버: 현재 날씨 요청
	WEATHER_RESULT,  // 서버 → 클라이언트: 조회 결과
	
	ACCOUNT_RECOVERY,
	ACCOUNT_RECOVERY_RESULT,
	
	EMAIL_CODE,
	EMAIL_CODE_RESULT,
	
	PROFILE_GET, // 내 프로필 조회 요청
	PROFILE_RESULT, // 프로필 조회 결과 응답
	
	PROFILE_UPDATE,
	PROFILE_UPDATE_RESULT,
	
    LOGIN,
    LOGIN_RESULT,
    CHECK_ID,
    SIGNUP,
    CREATE_CHAT,
    ENTER_CHAT, EXIT_CHAT, EXIT_CHAT_RESULT,
    MESSAGE,

    USER_LIST, CHAT_ROOM_LIST,
}

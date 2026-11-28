package dto.type;

public enum DtoType {
	STORED_CHAT, // 서버 → 방 참여자 전체: 저장된 일반 채팅과 message_id
	
    REPLY_MESSAGE,  // 클라이언트 → 서버: 답장 전송
    REPLY_RESULT,   // 서버 → 요청자: 저장 성공 또는 실패
    REPLY_NOTICE,   // 서버 → 방 참여자: 저장된 답장 메시지
	
    GALLERY_GET,          // 선택한 이미지 원본 요청
    GALLERY_IMAGE_BEGIN,  // 이미지 형식·크기·해시 수신
    GALLERY_IMAGE_CHUNK,  // 이미지 조각 수신
    GALLERY_IMAGE_END,    // 원본 전송 완료
	
    GALLERY_LIST,  // 채팅방 이미지 목록 요청
    GALLERY_ITEM,  // 이미지 정보 한 건
    GALLERY_END,   // 목록 조회 완료 또는 거부
	
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
	
	CHAT_SEARCH,
	CHAT_SEARCH_ITEM,
	CHAT_SEARCH_END,
	
	CHAT_HISTORY_BEGIN,
	CHAT_HISTORY_ITEM,
	CHAT_HISTORY_END,
	
	SCHEDULE,
	SCHEDULE_RESULT,
	
	INVITE_CHAT,
	INVITE_CHAT_RESULT,
	
	WEATHER,         // 현재 날씨 요청
	WEATHER_RESULT,  // 현재 날씨 조회 결과
	
	ACCOUNT_RECOVERY,
	ACCOUNT_RECOVERY_RESULT,
	
	EMAIL_CODE,
	EMAIL_CODE_RESULT,
	
	PROFILE_GET, // 내 프로필 조회 요청
	PROFILE_RESULT, // 프로필 조회 결과 응답
	PUBLIC_PROFILE_GET, // 상대 공개 프로필 조회
	PUBLIC_PROFILE_RESULT,
	
	PROFILE_UPDATE,
	PROFILE_UPDATE_RESULT,
	
    INIT,

    LOGIN,
    LOGIN_RESULT,
    CHECK_ID,
    SIGNUP,
    CREATE_CHAT,
    ENTER_CHAT, EXIT_CHAT, EXIT_CHAT_RESULT,
    MESSAGE,

    USER_LIST, CHAT_ROOM_LIST,
}

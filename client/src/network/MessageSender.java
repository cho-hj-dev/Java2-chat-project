package network;

import dto.request.*;
import dto.type.DtoType;

import java.io.PrintWriter;
import java.net.Socket;

public class MessageSender {

    Socket socket;

    public MessageSender(Socket socket) {
        this.socket = socket;
    }

    public synchronized boolean sendMessage(DTO dto) {
        DtoType type = dto.getType();
        // 종료된 연결에는 전송을 시도하지 않는다.
        if (socket.isClosed() || !socket.isConnected()) {
            return false;
        }

        try {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            switch (type) {
	            case ADMIN_ACCESS:
	                writer.println((AdminAccessRequest) dto);
	                break;
	            case PUBLIC_PROFILE_GET:
	                writer.println((PublicProfileRequest) dto);
	                break;
	            case REPLY_MESSAGE:
	                writer.println((ReplyMessageRequest) dto);
	                break;
	                
	            case FILE_START, FILE_CHUNK, FILE_END, FILE_GET:
	                // 네 요청 DTO의 toString()이 각각 통신 형식을 만든다.
	                writer.println(dto);
	                break;
	                
	            case GALLERY_GET:
	                writer.println((GalleryGetRequest) dto);
	                break;
	                
	            case GALLERY_LIST:
	                writer.println((GalleryListRequest) dto);
	                break;
            
	            case CHAT_SEARCH:
	                writer.println((ChatSearchRequest) dto);
	                break;
            
	            case INVITE_CHAT:
	                writer.println((InviteChatRequest) dto);
	                break;
            
	            case WEATHER:
	                // 서버에 현재 날씨 조회를 요청한다.
	                writer.println((WeatherRequest) dto);
	                break;
	            case ACCOUNT_RECOVERY:
	                writer.println((AccountRecoveryRequest) dto);
	                break;
	            case EMAIL_CODE:
	                writer.println((EmailCodeRequest) dto);
	                break;
	            case PROFILE_UPDATE:
	                writer.println((ProfileUpdateRequest) dto);
	                break;
            	case PROFILE_GET:
            		writer.println((ProfileRequest) dto);
            		break;
            	case CHECK_ID:
            		writer.println((CheckIdRequest) dto);
            		break;
            	case SIGNUP:
            	    writer.println((SignupRequest) dto);
            	    break;
                case LOGIN:
                    writer.println((LoginRequest) dto);
                    break;
                case CREATE_CHAT:
                    writer.println((CreateChatRoomRequest) dto);
                    break;
                case ENTER_CHAT:
                    writer.println((EnterChatRequest) dto);
                    break;
                case EXIT_CHAT:
                    writer.println((ExitChatRequest) dto);
                    break;
                case MESSAGE:
                    writer.println((MessageRequest) dto);
                    break;
                case SCHEDULE:
                    writer.println((ScheduleRequest) dto);
                    break;
                   
            }

            writer.flush();
            return !writer.checkError();
        }
        catch (Exception e) {
            // 연결 종료 시 긴 스택 트레이스 대신 오류 종류만 기록한다.
            System.err.println("메시지 전송 실패: "
                    + e.getClass().getSimpleName());
            return false;
        }

    }
}


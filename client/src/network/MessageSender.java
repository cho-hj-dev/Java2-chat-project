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

        try {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            switch (type) {
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
            }

            writer.flush();
            return !writer.checkError();
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }
}


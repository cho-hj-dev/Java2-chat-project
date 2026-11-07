package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SignupRequest {
	
    private final String loginId;
    private final String password;
    private final String nickname;
    private final String postalCode;
    private final String address;
    private final String addressDetail;
    private final String profileImage;
    
    public SignupRequest(String message) {
        String[] values = message.split(",", -1); // 잘못된 요청 형식이나 Base64 데이터는 예외가 발생하며, 다음 단계에서 서버가 이를 잡아 실패 응답을 보내도록. 
        if (values.length != 7) {
            throw new IllegalArgumentException("잘못된 회원가입 요청입니다.");
        }

        loginId = decode(values[0]);
        password = decode(values[1]);
        nickname = decode(values[2]);
        postalCode = decode(values[3]);
        address = decode(values[4]);
        addressDetail = decode(values[5]);
        profileImage = values[6];
    }
    
    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPostalCode() { return postalCode; }
    public String getAddress() { return address; }
    public String getAddressDetail() { return addressDetail; }
    public String getProfileImage() { return profileImage; }
}

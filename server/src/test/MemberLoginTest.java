package test;

import java.util.Arrays;

import dao.UserDao;

public class MemberLoginTest {
		
	    public static void main(String[] args) throws Exception {
	        UserDao dao = new UserDao();

	        String loginId = "week06_test01";
	        char[] correctPassword = "Abcd1234!".toCharArray();
	        char[] wrongPassword = "Wrong1234!".toCharArray();

	        try {
	            if (dao.findByLoginId(loginId).isEmpty()) {
	                throw new AssertionError("테스트 회원이 없습니다: " + loginId);
	            }

	            String missingId = "week06_missing01";
	            if (dao.findByLoginId(missingId).isPresent()) {
	                throw new AssertionError("없는 아이디 테스트용 값을 바꿔주세요.");
	            }

	            check("정상 비밀번호 인증",
	                    dao.authenticate(loginId, correctPassword).isPresent());

	            check("잘못된 비밀번호 인증 거부",
	                    dao.authenticate(loginId, wrongPassword).isEmpty());

	            check("없는 아이디 인증 거부",
	                    dao.authenticate(missingId, correctPassword).isEmpty());

	            System.out.println("로그인 DAO 테스트 모두 통과");
	        } finally {
	            Arrays.fill(correctPassword, '\0');
	            Arrays.fill(wrongPassword, '\0');
	        }
	    }

	    private static void check(String name, boolean passed) {
	        if (!passed) {
	            throw new AssertionError("[실패] " + name);
	        }
	        System.out.println("[통과] " + name);
	}

}

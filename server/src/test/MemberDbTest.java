package test;

import dao.UserDao;
import domain.Member;
import security.PasswordHasher;

import java.util.Arrays;
import java.util.UUID;

public class MemberDbTest {

    public static void main(String[] args) throws Exception {
        UserDao userDao = new UserDao();
        String loginId = "week04_test01";

        if (userDao.findByLoginId(loginId).isEmpty()) {
            char[] password = UUID.randomUUID().toString().toCharArray();

            try {
                String passwordHash = PasswordHasher.hash(password);
                int count = userDao.insert(
                        loginId, passwordHash, "4주차테스트");

                if (count != 1) {
                    throw new IllegalStateException("회원 등록 결과를 확인하세요.");
                }

                System.out.println("회원 등록 성공: " + count + "명");
            } finally {
                Arrays.fill(password, '\0');
            }
        } else {
            System.out.println("기존 테스트 회원이 있어 등록을 건너뜁니다.");
        }

        Member member = userDao.findByLoginId(loginId)
                .orElseThrow(() ->
                        new IllegalStateException("등록된 회원을 찾을 수 없습니다."));

        System.out.println("회원 조회 성공");
        System.out.println("회원 번호: " + member.getUserId());
        System.out.println("로그인 아이디: " + member.getLoginId());
        System.out.println("닉네임: " + member.getNickname());
        System.out.println("가입 시각: " + member.getCreatedAt());
    }
}
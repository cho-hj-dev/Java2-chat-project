package domain;

//record는 조회 결과처럼 데이터를 담는 데 사용하는 Java 문법 
public record MemberProfile(
        String loginId,
        String nickname,
        String postalCode,
        String address,
        String addressDetail,
        String imagePath
) {
}

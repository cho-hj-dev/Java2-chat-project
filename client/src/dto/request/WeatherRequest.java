package dto.request;

import dto.type.DtoType;

public class WeatherRequest extends DTO {

    public WeatherRequest() {
        // DTO의 toString()이 "WEATHER:"를 만든다.
        // 날씨 조회는 별도 입력값이 필요하지 않다.
        super(DtoType.WEATHER);
    }
}
package service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WeatherService {

    // 첫 구현에서는 서울 날씨를 조회한다.
    private static final String LATITUDE = "37.5665";
    private static final String LONGITUDE = "126.9780";

    // 여러 사용자가 접속해도 10분 동안은 이전 조회 결과를 재사용한다.
    private static final Duration CACHE_TIME = Duration.ofMinutes(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private WeatherInfo cachedWeather;
    private Instant cachedAt = Instant.EPOCH;

    // 로비 화면에 표시할 값만 담는다.
    public record WeatherInfo(
            String city, double temperature, String description) {
    }

    public synchronized WeatherInfo getCurrentWeather()
            throws IOException, InterruptedException {

        // 최근 조회한 날씨가 있으면 외부 API를 다시 호출하지 않는다.
        if (cachedWeather != null
                && Instant.now().isBefore(cachedAt.plus(CACHE_TIME))) {
            return cachedWeather;
        }

        String apiKey = System.getenv("OPENWEATHER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENWEATHER_API_KEY 환경 변수가 없습니다.");
        }

        // API 키는 서버에서만 사용하며 요청 URL을 로그에 출력하지 않는다.
        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?lat=" + LATITUDE
                + "&lon=" + LONGITUDE
                + "&units=metric"
                + "&lang=kr"
                + "&appid=" + URLEncoder.encode(
                        apiKey, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException(
                    "날씨 API 요청 실패: HTTP " + response.statusCode());
        }

        try {
            // JSON 응답에서 지역명·기온·날씨 설명을 꺼낸다.
            JsonObject root =
                    JsonParser.parseString(response.body()).getAsJsonObject();

            String city = root.get("name").getAsString();
            double temperature = root.getAsJsonObject("main")
                    .get("temp").getAsDouble();
            String description = root.getAsJsonArray("weather")
                    .get(0).getAsJsonObject()
                    .get("description").getAsString();

            WeatherInfo result =
                    new WeatherInfo(city, temperature, description);

            cachedWeather = result;
            cachedAt = Instant.now();
            return result;
        } catch (RuntimeException exception) {
            throw new IOException(
                    "날씨 API의 JSON 응답을 해석하지 못했습니다.",
                    exception);
        }
    }

    // 서버 채팅 기능에 연결하기 전에 API 호출만 시험한다.
    public static void main(String[] args) {
        try {
            WeatherInfo weather =
                    new WeatherService().getCurrentWeather();

            System.out.printf(
                    "지역: %s / 기온: %.1f°C / 날씨: %s%n",
                    weather.city(),
                    weather.temperature(),
                    weather.description());
        } catch (Exception exception) {
            System.err.println(exception.getMessage());
        }
    }
}
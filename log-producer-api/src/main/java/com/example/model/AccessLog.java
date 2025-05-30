package com.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.ref.PhantomReference;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessLog {
    private String userId;
    private String ipAddress;
    private String userAgent;
    private String requestUrl;
    private String method;
    private LocalDateTime accessTime;

    // 테스트를 위해 랜덤 로그를 생성하는 헬퍼 메서드
    public static AccessLog createRandomLog() {
        return new AccessLog(
                "user-" + (int)(Math.random() * 1000), // 0부터 999까지의 랜덤 ID
                "192.168.0." + (int)(Math.random() * 255), // 랜덤 IP
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", // 임의의 User Agent
                "/api/data/" + (int)(Math.random() * 10), // 랜덤 URL 경로
                (Math.random() > 0.5 ? "GET" : "POST"), // GET 또는 POST
                LocalDateTime.now() // 현재 시간
        );
    }
}

package com.example.web;

import com.example.model.AccessLog;
import com.example.service.kafka.LogProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogApiController {

    private final LogProducerService logProducerService;

    @GetMapping("/generate-random")
    public String generateRandomLog() {

        AccessLog randomLog = AccessLog.createRandomLog();
        logProducerService.sendLog(randomLog); // 생성된 로그를 카프카로 전송
        log.info("Log : {}", randomLog);
        return "랜덤 로그를 카프카로 전송했습니다: " + randomLog.getUserId();
    }

}

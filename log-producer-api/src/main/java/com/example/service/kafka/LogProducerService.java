package com.example.service.kafka;

import com.example.model.AccessLog;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogProducerService {

    private final KafkaTemplate<String, AccessLog> kafkaTemplate;

    // AccessLog 객체를 카프카로 전송하는 메서드
    public void sendLog(AccessLog log) {
        // kafkaTemplate.sendDefault(log)는 application.yml에 설정된 default-topic("user-access-log")으로 메시지를 보냄.
        kafkaTemplate.sendDefault(log);
        System.out.println("로그 전송 완료: " + log.getUserId() + " - " + log.getRequestUrl() + " at " + log.getAccessTime());
    }

    // (선택 사항) 특정 토픽으로 명시적으로 메시지를 보내고 싶다면 이렇게도 가능.
    public void sendLogToTopic(String topic, AccessLog log) {
        kafkaTemplate.send(topic, log);
        System.out.println("로그 전송 완료 (지정 토픽 '" + topic + "'): " + log.getUserId());
    }
}

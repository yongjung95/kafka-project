package com.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;

    // 만약 Job 빈이 여러 개일 경우, @Qualifier로 특정 Job을 지정해주는 것이 좋다.
    // AccessLogBatchConfig에 정의된 Job 빈의 이름
    @Qualifier("accessLogProcessJob") // 실행할 Job 빈의 이름을 명시.
    private final Job accessLogProcessJob;

    @Scheduled(cron = "0 */1 * * * ?")
    public void runAccessLogJobEvery5Minutes() {
        try {
            log.info("매 2분 주기 accessLogProcessJob 실행 시작...");
            JobParameters jobParameters = new JobParametersBuilder()
                    // Job 실행마다 고유한 파라미터를 주는 것이 좋다. (중복 실행 방지 및 추적 용이)
                    .addString("JobID", String.valueOf(System.currentTimeMillis()))
                    .addLocalDateTime("runDateTime", LocalDateTime.now()) // 실행 시간 파라미터 추가
                    .toJobParameters();

            jobLauncher.run(accessLogProcessJob, jobParameters); // 지정된 Job과 파라미터로 Job을 실행.
            log.info("매 2분 주기 accessLogProcessJob 실행 완료.");

        } catch (Exception e) {
            log.error("매 2분 주기 accessLogProcessJob 실행 중 예외 발생: {}", e.getMessage(), e);
        }
    }
}

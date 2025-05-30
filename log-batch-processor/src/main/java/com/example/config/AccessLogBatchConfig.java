package com.example.config;

import com.example.model.AccessLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.kafka.builder.KafkaItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AccessLogBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Value("${spring.kafka.consumer.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.properties.spring.json.value.default.type}")
    private String defaultType;

    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages}")
    private String trustedPackages;

    @Bean
    public Job accessLogProcessJob() {
        log.info(">>>> accessLogProcessJob started.");
        return new JobBuilder("accessLogProcessJob", jobRepository)
                .start(accessLogStep())
                .build();
    }

    // --- Step 정의 ---
    @Bean
    public Step accessLogStep() {
        log.info(">>>> accessLogStep started.");
        return new StepBuilder("accessLogStep", jobRepository) // Step의 이름은 "accessLogStep"
                .<AccessLog, AccessLog>chunk(100, transactionManager) // 청크 기반 처리 설정
                .reader(kafkaItemReader()) // ItemReader 지정
                .processor(accessLogProcessor()) // ItemProcessor 지정
                .writer(jpaAccessLogWriter()) // ItemWriter 지정
                .faultTolerant() // 오류 처리 기능 활성화
                .skipLimit(10) // 최대 10번까지 아이템 처리 중 예외 발생 시 스킵
                .skip(Exception.class) // Exception 및 그 하위 예외 발생 시 스킵 (운영 시 구체적 예외로 변경 권장)
                .build(); // Step 생성 완료!
    }

    // --- ItemReader 정의 (Kafka에서 데이터 읽기) ---
    @Bean
    public ItemReader<AccessLog> kafkaItemReader() {
        log.info(">>>> KafkaItemReader created.");
        Properties props = new Properties(); // 카프카 컨슈머 관련 설정을 담을 객체
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers); // 카프카 서버 주소
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);                   // 컨슈머 그룹 ID (같은 그룹 ID끼리 메시지를 나눠서 소비)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer"); // 메시지 키 역직렬화 방법
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonDeserializer"); // 메시지 값 역직렬화 방법 (JSON)
        props.put("spring.json.value.default.type", defaultType); // JSON 값을 어떤 타입의 객체로 만들지 (AccessLog)
        props.put("spring.json.trusted.packages", trustedPackages); // 신뢰할 수 있는 패키지 (보안)
//        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // 오프셋 초기화 방지
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // props.put(JsonDeserializer.FAIL_IF_UNKNOWN_PROPERTIES, "false"); // DTO에 없는 필드가 JSON에 있어도 에러 안 나게 하려면

        return new KafkaItemReaderBuilder<String, AccessLog>() // KafkaItemReader를 만드는 빌더
                .consumerProperties(props) // 위에서 설정한 컨슈머 속성들을 전달
                .name("accessLogKafkaReader") // 이 Reader의 이름을 지정 (배치 메타데이터에 기록)
                .topic("user-access-log") // 구독할 카프카 토픽 이름
                .partitions(0) // Topic 의 partition 을 지정하며, 여러 partition 지정이 가능하다.
                .partitionOffsets(new HashMap<>()) // KafkaItemReader 는 offset 을 지정하지 않으면 0번 offset 부터 읽기 때문에, 빈 맵을 넣어주면 마지막 offset 부터 데이터를 읽어온다.
                .pollTimeout(Duration.ofSeconds(5L)) // 카프카에서 메시지를 기다리는 최대 시간
                .build(); // ItemReader 생성!
    }

    // --- ItemProcessor 정의 (데이터 가공) ---
    @Bean
    public ItemProcessor<AccessLog, AccessLog> accessLogProcessor() {
        log.info(">>>> ItemProcessor created.");
        return logEntry -> { // 입력도 AccessLog, 출력도 AccessLog 타입
            if (logEntry == null) { // 혹시 모를 null 레코드 방어
                return null; // null을 반환하면 이 아이템은 Writer로 넘어가지 않고 그냥 스킵
            }
            log.debug("Processing log with User ID: {}", logEntry.getUserId());
            return logEntry;
        };
    }

    // --- ItemWriter 정의 (DB에 데이터 저장) ---
    @Bean
    public JdbcBatchItemWriter<AccessLog> jpaAccessLogWriter() {
        log.info(">>>> JpaItemWriter created.");
        return new JdbcBatchItemWriterBuilder<AccessLog>()
                .dataSource(dataSource) // DB 접속 정보
                .sql("INSERT INTO access_log (access_time, ip_address, method, request_url, user_agent, user_id) " +
                        "VALUES (:accessTime, :ipAddress, :method, :requestUrl, :userAgent, :userId)") // 명명된 파라미터 사용 예시
                .beanMapped() // AccessLog 객체의 필드 이름과 SQL의 명명된 파라미터(:필드명)를 자동으로 매핑
                .build();
    }
}
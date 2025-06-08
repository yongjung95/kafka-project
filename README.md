# 카프카를 이용한 실시간 사용자 접속 로그 처리 배치 시스템

## 프로젝트 진행 이유
- 킥더허들 재직 중 백엔드 API 서버 개발 업무를 담당하면서 실시간으로 발생하는 대량의 사용자 접속 로그 API 요청 처리와 함께 운영 데이터베이스에 Insert 되는 것을 발견.
- 이러한 방식은 특히 '실시간 쿠폰 및 추천인 이벤트'와 같이 순간적으로 부하가 몰리는 상황에서, 몇 가지 잠재적인 문제점이 야기될 수 있다고 생각이 들었다.
### 주요 문제점
- 운영 DB 과부하로 인한 서비스 속도 저하 및 안정성 위협
- 트래픽 증가 시 확장성 확보의 어려움

## 목표
- 로그 처리를 비동기화하여 API 서버와 운영 DB의 부담을 줄인다.
- Kafka로 로그를 안정적으로 수집 후 Spring Batch를 통해 DB에 효율적으로 일괄 적재(벌크 연산)하는 시스템을 구축한다.
- 대용량 데이터 처리 기술(Kafka, Spring Batch) 활용 능력 및 시스템 구축 경험 확보.


## 주요 기능
- API 서버에서 Kafka에 사용자 접속 로그 저장.
- KafKa에서 로그를 읽어와 스프링 배치 서버를 이용하여 DB에 로그 저장.


## 사용 기술
- Spring Framework, Spring Batch, Docker, Kafka, MariaDB


## 핵심 학습 내용 및 문제 해결 과정
### Kafka 오프셋 문제
- 문제 상황
  - Spring Batch 애플리케이션을 재시작할 때마다 Kafka 토픽의 데이터를 처음부터 다시 읽어와서 중복 처리되는 현상 발생. 
  - enable.auto.commit=true, auto.offset.reset='latest', group.id 설정은 올바르게 되어 있는 것으로 보였음.
- 진단 과정
  - kafka-consumer-groups.sh 스크립트를 Docker 컨테이너 내에서 실행하여 실제 Kafka 브로커에 커밋된 오프셋 확인.
  - Kafka CLI를 통해 CURRENT-OFFSET이 정상적으로 증가하고 LAG이 0인 것을 확인했음에도, 새 Job Instance에서는 여전히 처음부터 읽는 문제 지속. 
  - 스케줄러 실행(매번 새 Job Parameter)과 수동 실행(Job Parameter 동일/없음) 시 동작 차이 분석을 통해, 문제가 새로운 Job Instance에서 발생함을 특정.
- 원인 분석
  - KafkaItemReader가 특정 파티션(partitions(0))을 할당받고, 해당 Job Instance의 ExecutionContext에 저장된 오프셋이 없을 경우 
  - 즉 새로운 Job Instance일 때, Kafka 그룹에 커밋된 오프셋을 사용하지 않고 할당된 파티션의 처음(offset 0)부터 읽으려고 하는 기본 동작을 가지고 있음을 발견.
- 사용한 해결 방법
  - KafkaItemReaderBuilder에 .partitionOffsets(new HashMap<>())를 추가. 
  - 이 호출을 통해 KafkaItemReader의 기본 동작 모드를 변경하여, 명시적으로 지정된 오프셋이 없을 경우 카프카 컨슈머의 표준 오프셋 처리 방식(커밋된 오프셋 또는 auto.offset.reset 정책 따름)을 따르도록 유도함.

### JpaItemWriter와 배치 Insert
- 문제 상황
  - JpaItemWriter를 사용하고 application.yml에 hibernate.jdbc.batch_size를 설정했음에도 불구하고, 실제로는 INSERT 쿼리가 개별적으로 실행되어 배치 처리의 이점을 얻지 못함.
- 진단 과정
  - Hibernate의 show-sql: true 옵션으로 실행되는 쿼리 확인.
- 원인 분석
  - AccessLog 엔티티의 ID 생성 전략이 MariaDB의 AUTO_INCREMENT와 매핑되는 GenerationType.IDENTITY였음.
  - IDENTITY 전략은 DB에 실제 INSERT가 이루어져야 ID가 확정되므로, Hibernate는 배치 처리를 위해 INSERT 문을 모을 수 없고 각 건마다 즉시 실행해야 함을 이해.
- 해결 방법
  - ID 생성 전략을 GenerationType.SEQUENCE 또는 애플리케이션 레벨 UUID 생성으로 변경하는 방안.
  - JdbcBatchItemWriter 벌크 연산을 사용하는 방안.
- 사용한 해결 방법
  - 기존 JpaItemWriter와 IDENTITY 전략을 유지할 경우, 배치 INSERT를 위해서는 ID 생성 전략을 SEQUENCE 등으로 변경해야 했고, 이 과정이 프로젝트의 현재 범위에 비해 다소 복잡하다고 판단.
  - 따라서 JdbcBatchItemWriter를 사용하여 직접 INSERT 쿼리를 작성하고 JDBC 레벨의 배치 연산을 활용하는 방식을 최종적으로 선택.


## 추후 학습 및 개선 희망 사항
### Kafka 오프셋 문제
- .partitionOffsets(new HashMap<>())를 통해 초기 오프셋 문제를 해결했지만, 이 해결책이 KafkaItemReader의 내부 로직에 정확히 어떤 영향을 미치는지 이해가 필요함.
- 그리고 다양한 상황에서 발생할 수 있는 오프셋 관련 이슈들에 대해 더욱 이해가 필요하다고 느낌.

## 추가 학습 사항 및 문제 해결
### Kafka 오프셋 문제
- .partitionOffsets(new HashMap<>())의 KafkaItemReader의 내부 로직
  - KafkaItemReader를 수동 할당 모드로 전환시키면서, 각 파티션의 시작 지점은 카프카에 커밋된 오프셋을 우선적으로 따르도록 한다.
  - 주된 목적으로는 배치 Job이 재실행될 때마다 매번 0번 오프셋부터 모든 데이터를 다시 읽는 것을 방지하고, 마지막으로 작업했던 부분부터 이어서 처리하고 싶을 때 사용한다.
  - 하지만 이 모든 것은 .partitions()을 사용하여 파티션이 명시적으로 지정되었을 때만 의미가 있다.
  - ⭐️ **파티션은 내가 수동으로 정하겠지만, 각 파티션을 어디서부터 읽을지(오프셋)는 카프카의 기본 룰을 따를게.** ⭐️

### 스케줄러로 잡(Job) 재실행 시, 마지막 메시지만 무한 반복 처리
- Spring Batch 잡을 스케줄러로 주기적으로 실행했을 때, 첫 실행에서는 데이터를 잘 처리하지만 그 이후부터는 새로운 메시지가 카프카에 들어와도 더 이상 읽지 못하고, 이전에 읽었던 마지막 메시지를 계속해서 읽고 DB에 중복 저장하는 문제가 발생.
  - @Bean의 싱글톤(Singleton) 상태 문제
  - Spring에서 @Bean으로 등록된 KafkItemReader는 기본적으로 싱글톤이다.
    - 즉, 애플리케이션 실행 시 한 번만 생성되어 계속 재사용된다.
  - 스케줄러가 잡을 다시 실행해도, 이미 작업이 끝난 상태의 동일한 ItemReader 객체를 재사용하므로 새로운 메시지를 읽어오지 못했다.
  - 해결 방법
    - @StepScope 어노테이션을 이용하여 스텝이 시작될 때 Bean을 지연 생성한다.


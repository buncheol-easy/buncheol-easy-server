package buncheoleasy.push.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.notification.application.NotificationInboxRecorder;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushMessage;
import buncheoleasy.push.domain.PushPlatform;
import buncheoleasy.push.dto.request.RegisterPushDeviceRequest;
import buncheoleasy.push.infrastructure.ApnsClient;
import buncheoleasy.push.infrastructure.ApnsSendResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수신함 저장(커밋) → 이벤트 → 비동기 리스너 → 발송기까지 실제 빈으로 잇는다. 발송기({@link ApnsClient})만 가짜다 — 애플 서버를 부르지 않는다.
 *
 * <p>커밋 뒤에만 도는 리스너를 보려면 테스트가 트랜잭션을 커밋해야 해서, 클래스에 {@code @Transactional} 을 걸지 않고 만든 행을 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("수신함 알림 → 앱 푸시 연결 테스트")
class PushNotificationEventIntegrationTest {

  private static final String TOKEN = "cd".repeat(32);
  private static final Map<String, String> VARIABLES =
      Map.of(
          "닉네임", "참여자",
          "분철명", "아이브 앨범",
          "멤버명", "장원영",
          "입금금액", "32,000");

  @Autowired private NotificationInboxRecorder recorder;
  @Autowired private PushDeviceService pushDeviceService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private ApnsClient apnsClient;

  private Long userId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "push-event");
    pushDeviceService.register(
        userId, new RegisterPushDeviceRequest(TOKEN, PushPlatform.IOS, PushEnvironment.PRODUCTION));
    given(apnsClient.isEnabled()).willReturn(true);
    given(apnsClient.send(any(), any())).willReturn(ApnsSendResult.SENT);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM push_devices WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM inbox_messages WHERE recipient_id = ?", userId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void 수신함에_알림이_저장되면_커밋_뒤_수신자_기기로_같은_알림을_보낸다() {
    recorder.record(userId, AlimtalkTemplate.PAYMENT_CONFIRMED, VARIABLES);

    ArgumentCaptor<PushDevice> device = ArgumentCaptor.forClass(PushDevice.class);
    ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
    verify(apnsClient, timeout(5000)).send(device.capture(), message.capture());

    String savedDescription =
        jdbcTemplate.queryForObject(
            "SELECT description FROM inbox_messages WHERE recipient_id = ?", String.class, userId);
    assertThat(device.getValue().getToken()).isEqualTo(TOKEN);
    assertThat(device.getValue().getEnvironment()).isEqualTo(PushEnvironment.PRODUCTION);
    assertThat(message.getValue())
        .isEqualTo(
            new PushMessage(
                AlimtalkTemplate.PAYMENT_CONFIRMED.subject(), savedDescription, "/profile/bids"));
  }

  @Test
  void 애플이_멈춰도_수신함_기록은_기다리지_않고_푸시는_전용_풀에서_보낸다() throws Exception {
    // 운영에서 record() 를 부르는 것은 알림톡 단일 스레드다 — 여기서 애플을 기다리면 알림톡 줄이 막힌다.
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<String> sendThread = new AtomicReference<>();
    given(apnsClient.send(any(), any()))
        .willAnswer(
            invocation -> {
              sendThread.set(Thread.currentThread().getName());
              entered.countDown();
              release.await(10, TimeUnit.SECONDS);
              return ApnsSendResult.SENT;
            });
    try {
      long started = System.nanoTime();
      recorder.record(userId, AlimtalkTemplate.PAYMENT_CONFIRMED, VARIABLES);
      Duration recordTook = Duration.ofNanos(System.nanoTime() - started);

      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      // 발송은 최대 10초 멈춰 있다 — 같은 스레드에서 돌았다면 record() 도 그만큼 걸린다.
      assertThat(recordTook).isLessThan(Duration.ofSeconds(5));
      assertThat(sendThread.get()).startsWith("push-");
    } finally {
      release.countDown();
    }
  }

  @Test
  void 수신함_저장이_롤백되면_푸시도_나가지_않는다() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              recorder.record(userId, AlimtalkTemplate.PAYMENT_CONFIRMED, VARIABLES);
              status.setRollbackOnly();
            });

    verify(apnsClient, after(1000).never()).send(any(), any());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_messages WHERE recipient_id = ?", Long.class, userId))
        .isZero();
  }
}

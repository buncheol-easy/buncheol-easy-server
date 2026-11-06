package buncheoleasy.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushDeviceRepository;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushPlatform;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaPushDeviceRepositoryAdapter 테스트")
class JpaPushDeviceRepositoryAdapterTest {

  private static final String TOKEN_A = "a".repeat(64);
  private static final String TOKEN_B = "b".repeat(64);
  private static final String TOKEN_C = "c".repeat(64);

  @Autowired private PushDeviceRepository pushDeviceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long userId;
  private Long otherUserId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "push-owner");
    otherUserId = TestUserFixture.insertUser(jdbcTemplate, "push-other");
  }

  private void upsert(final Long ownerId, final String token, final PushEnvironment environment) {
    pushDeviceRepository.upsert(PushDevice.create(ownerId, PushPlatform.IOS, token, environment));
  }

  private Long countByToken(final String token) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM push_devices WHERE token = ?", Long.class, token);
  }

  @Nested
  @DisplayName("upsert")
  class UpsertTest {

    @Test
    void 처음_보는_토큰이면_행을_넣는다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);

      List<PushDevice> devices = pushDeviceRepository.findAllByUserId(userId);
      assertThat(devices).hasSize(1);
      PushDevice device = devices.get(0);
      assertThat(device.getToken()).isEqualTo(TOKEN_A);
      assertThat(device.getPlatform()).isEqualTo(PushPlatform.IOS);
      assertThat(device.getEnvironment()).isEqualTo(PushEnvironment.SANDBOX);
      assertThat(device.getCreatedAt()).isNotNull();
      assertThat(device.getUpdatedAt()).isNotNull();
    }

    @Test
    void 같은_토큰을_다른_계정이_등록하면_주인과_환경만_바뀌고_행은_하나다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);

      upsert(otherUserId, TOKEN_A, PushEnvironment.PRODUCTION);

      assertThat(countByToken(TOKEN_A)).isEqualTo(1L);
      assertThat(pushDeviceRepository.findAllByUserId(userId)).isEmpty();
      List<PushDevice> others = pushDeviceRepository.findAllByUserId(otherUserId);
      assertThat(others).hasSize(1);
      assertThat(others.get(0).getEnvironment()).isEqualTo(PushEnvironment.PRODUCTION);
    }

    @Test
    void 같은_계정이_다시_등록해도_행은_하나고_갱신_시각이_바뀐다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);
      Timestamp old = Timestamp.valueOf("2020-01-01 00:00:00");
      jdbcTemplate.update("UPDATE push_devices SET updated_at = ? WHERE token = ?", old, TOKEN_A);

      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);

      assertThat(countByToken(TOKEN_A)).isEqualTo(1L);
      Timestamp updatedAt =
          jdbcTemplate.queryForObject(
              "SELECT updated_at FROM push_devices WHERE token = ?", Timestamp.class, TOKEN_A);
      assertThat(updatedAt).isAfter(old);
    }
  }

  @Nested
  @DisplayName("findIdsToTrim")
  class FindIdsToTrimTest {

    @Test
    void 최근_등록_순으로_keep_개를_넘는_기기의_id_를_돌려준다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);
      upsert(userId, TOKEN_B, PushEnvironment.SANDBOX);
      upsert(userId, TOKEN_C, PushEnvironment.SANDBOX);
      // A 가 가장 오래, C 가 가장 최근에 등록된 상태로 고정한다(같은 트랜잭션 안의 DB 시각은 같을 수 있다).
      setUpdatedAt(TOKEN_A, "2020-01-01 00:00:00");
      setUpdatedAt(TOKEN_B, "2020-01-02 00:00:00");
      setUpdatedAt(TOKEN_C, "2020-01-03 00:00:00");

      List<Long> ids = pushDeviceRepository.findIdsToTrim(userId, 2);

      assertThat(ids).containsExactly(idOf(TOKEN_A));
    }

    @Test
    void keep_개_이하면_빈_리스트다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);

      assertThat(pushDeviceRepository.findIdsToTrim(userId, 2)).isEmpty();
    }

    @Test
    void 다른_회원의_기기는_세지_않는다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);
      upsert(otherUserId, TOKEN_B, PushEnvironment.SANDBOX);

      assertThat(pushDeviceRepository.findIdsToTrim(userId, 1)).isEmpty();
    }

    private void setUpdatedAt(final String token, final String at) {
      jdbcTemplate.update(
          "UPDATE push_devices SET updated_at = ? WHERE token = ?", Timestamp.valueOf(at), token);
    }

    private Long idOf(final String token) {
      return jdbcTemplate.queryForObject(
          "SELECT id FROM push_devices WHERE token = ?", Long.class, token);
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTest {

    @Test
    void 본인_토큰만_지운다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);
      upsert(otherUserId, TOKEN_B, PushEnvironment.SANDBOX);

      assertThat(pushDeviceRepository.deleteOwnedByToken(userId, TOKEN_B)).isZero();
      assertThat(pushDeviceRepository.deleteOwnedByToken(userId, TOKEN_A)).isEqualTo(1);

      assertThat(countByToken(TOKEN_A)).isZero();
      assertThat(countByToken(TOKEN_B)).isEqualTo(1L);
    }

    @Test
    void 없는_토큰을_지워도_0행이다() {
      assertThat(pushDeviceRepository.deleteOwnedByToken(userId, TOKEN_A)).isZero();
      assertThat(pushDeviceRepository.deleteByToken(TOKEN_A)).isZero();
    }

    @Test
    void 무효_토큰은_주인과_무관하게_지운다() {
      upsert(otherUserId, TOKEN_A, PushEnvironment.SANDBOX);

      assertThat(pushDeviceRepository.deleteByToken(TOKEN_A)).isEqualTo(1);
      assertThat(countByToken(TOKEN_A)).isZero();
    }

    @Test
    void 회원의_기기를_모두_지우고_다른_회원은_남긴다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);
      upsert(userId, TOKEN_B, PushEnvironment.PRODUCTION);
      upsert(otherUserId, TOKEN_C, PushEnvironment.SANDBOX);

      assertThat(pushDeviceRepository.deleteAllByUserId(userId)).isEqualTo(2);

      assertThat(pushDeviceRepository.findAllByUserId(userId)).isEmpty();
      assertThat(pushDeviceRepository.findAllByUserId(otherUserId)).hasSize(1);
    }

    @Test
    void id_목록으로_지운다_빈_목록도_받는다() {
      upsert(userId, TOKEN_A, PushEnvironment.SANDBOX);
      Long id = pushDeviceRepository.findAllByUserId(userId).get(0).getId();

      pushDeviceRepository.deleteAllByIdIn(List.of());
      pushDeviceRepository.deleteAllByIdIn(List.of(id));
      em.flush();
      em.clear();

      assertThat(pushDeviceRepository.findAllByUserId(userId)).isEmpty();
    }
  }
}

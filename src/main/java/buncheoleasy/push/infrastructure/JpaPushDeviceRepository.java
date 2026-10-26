package buncheoleasy.push.infrastructure;

import buncheoleasy.push.domain.PushDevice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaPushDeviceRepository extends JpaRepository<PushDevice, Long> {

  List<PushDevice> findAllByUserId(Long userId);

  // 조회 후 저장(2단계)이면 앱이 같은 토큰을 연달아 등록할 때 늦은 쪽이 유니크 충돌(500)로 죽는다. 한 문장이면 MySQL 이 행 잠금으로 줄 세운다.
  // 이 표는 upsert 라 created_at·updated_at 에 DB 시각을 쓴다(다른 표의 JPA 저장은 앱 시각). updated_at 은 값이 같아도 갱신되도록 명시한다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO push_devices (user_id, platform, token, environment) "
              + "VALUES (:userId, :platform, :token, :environment) "
              + "ON DUPLICATE KEY UPDATE user_id = :userId, platform = :platform, "
              + "environment = :environment, updated_at = CURRENT_TIMESTAMP",
      nativeQuery = true)
  int upsert(
      @Param("userId") Long userId,
      @Param("platform") String platform,
      @Param("token") String token,
      @Param("environment") String environment);

  // 정상 상태에선 0~1개. LIMIT 1000 은 상한을 넘긴 회원도 한 번에 정리할 안전 상한(최근 검색어 정리와 같은 모양).
  @Query(
      value =
          "SELECT id FROM push_devices "
              + "WHERE user_id = :userId "
              + "ORDER BY updated_at DESC, id DESC "
              + "LIMIT 1000 OFFSET :keep",
      nativeQuery = true)
  List<Long> findIdsToTrim(@Param("userId") Long userId, @Param("keep") int keep);

  // 파생 delete 는 SELECT 후 em.remove 2단계라 같은 토큰 삭제가 겹치면 늦은 쪽이 StaleStateException(500)으로 죽는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PushDevice d WHERE d.userId = :userId AND d.token = :token")
  int deleteOwnedByToken(@Param("userId") Long userId, @Param("token") String token);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PushDevice d WHERE d.token = :token")
  int deleteByToken(@Param("token") String token);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PushDevice d WHERE d.userId = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
}

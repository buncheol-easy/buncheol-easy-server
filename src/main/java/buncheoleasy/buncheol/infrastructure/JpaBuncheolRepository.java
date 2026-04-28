package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolRepository extends JpaRepository<Buncheol, Long> {

  /** status 가 expectedStatus 인 경우에만 status 를 갱신한다 (compare-and-swap). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :newStatus, "
          + "    b.updatedAt = :now "
          + "WHERE b.id = :id AND b.status = :expectedStatus")
  int updateStatusIfMatches(
      @Param("id") Long id,
      @Param("newStatus") BuncheolStatus newStatus,
      @Param("now") LocalDateTime now,
      @Param("expectedStatus") BuncheolStatus expectedStatus);
}

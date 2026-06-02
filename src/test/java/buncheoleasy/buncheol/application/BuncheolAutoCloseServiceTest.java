package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolAutoCloseService 단위 테스트")
class BuncheolAutoCloseServiceTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

  @InjectMocks private BuncheolAutoCloseService buncheolAutoCloseService;

  @Mock private BuncheolRepository buncheolRepository;

  @Mock private ParticipationDomainService participationDomainService;

  @Nested
  @DisplayName("만료 분철 조회 테스트")
  class FindExpiredBuncheolIdsTest {

    @Test
    void deadline이_지난_RECRUITING_분철_id를_조회한다() {
      given(buncheolRepository.findRecruitingIdsPastDeadline(NOW, 100))
          .willReturn(List.of(1L, 2L, 3L));

      List<Long> result = buncheolAutoCloseService.findExpiredBuncheolIds(NOW);

      assertThat(result).containsExactly(1L, 2L, 3L);
    }
  }

  @Nested
  @DisplayName("단일 분철 자동 마감 테스트")
  class CloseExpiredTest {

    @Test
    void CAS_마감에_성공하면_낙찰자를_선정하고_true를_반환한다() {
      given(buncheolRepository.closeIfRecruiting(BUNCHEOL_ID, NOW)).willReturn(1);

      boolean result = buncheolAutoCloseService.closeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      then(participationDomainService).should().selectWinners(BUNCHEOL_ID, NOW);
    }

    @Test
    void CAS_마감에_실패하면_낙찰자를_선정하지_않고_false를_반환한다() {
      given(buncheolRepository.closeIfRecruiting(BUNCHEOL_ID, NOW)).willReturn(0);

      boolean result = buncheolAutoCloseService.closeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isFalse();
      then(participationDomainService).shouldHaveNoInteractions();
    }
  }
}

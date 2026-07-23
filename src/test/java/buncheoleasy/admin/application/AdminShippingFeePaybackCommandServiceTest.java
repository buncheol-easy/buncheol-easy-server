package buncheoleasy.admin.application;

import static org.mockito.BDDMockito.then;

import buncheoleasy.admin.dto.request.AdminShippingFeePaybackActionRequest;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminShippingFeePaybackCommandService 테스트")
class AdminShippingFeePaybackCommandServiceTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

  @Mock private ParticipationDomainService participationDomainService;

  private AdminShippingFeePaybackCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminShippingFeePaybackCommandService(
            participationDomainService, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void COMPLETE_액션은_입금_완료_전이를_위임한다() {
    service.process(
        PARTICIPATION_ID,
        new AdminShippingFeePaybackActionRequest(
            AdminShippingFeePaybackActionRequest.Action.COMPLETE, null));

    then(participationDomainService).should().completePayback(PARTICIPATION_ID, NOW);
  }

  @Test
  void REJECT_액션은_사유와_함께_반려_전이를_위임한다() {
    service.process(
        PARTICIPATION_ID,
        new AdminShippingFeePaybackActionRequest(
            AdminShippingFeePaybackActionRequest.Action.REJECT, "비공개 계정이라 확인 불가"));

    then(participationDomainService)
        .should()
        .rejectPayback(PARTICIPATION_ID, "비공개 계정이라 확인 불가");
  }
}

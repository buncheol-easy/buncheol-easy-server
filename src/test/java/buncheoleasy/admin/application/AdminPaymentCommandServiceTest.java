package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPaymentCommandService 단위 테스트")
class AdminPaymentCommandServiceTest {

  @InjectMocks private AdminPaymentCommandService adminPaymentCommandService;

  @Mock private ParticipationService participationService;

  @Nested
  @DisplayName("confirmPayments 테스트")
  class ConfirmPaymentsTest {

    @Test
    void 모든_참여를_건별로_입금확인한다() {
      // given
      willDoNothing().given(participationService).confirmPaymentByAdmin(1L);
      willDoNothing().given(participationService).confirmPaymentByAdmin(2L);

      // when
      AdminBulkResultResponse result =
          adminPaymentCommandService.confirmPayments(List.of(1L, 2L));

      // then
      assertThat(result.succeededIds()).containsExactly(1L, 2L);
      assertThat(result.failures()).isEmpty();
      then(participationService).should().confirmPaymentByAdmin(1L);
      then(participationService).should().confirmPaymentByAdmin(2L);
    }

    @Test
    void 일부_실패해도_나머지_건은_계속_처리하고_실패_사유를_모아_돌려준다() {
      // given
      willDoNothing().given(participationService).confirmPaymentByAdmin(1L);
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID))
          .given(participationService)
          .confirmPaymentByAdmin(2L);
      willDoNothing().given(participationService).confirmPaymentByAdmin(3L);

      // when
      AdminBulkResultResponse result =
          adminPaymentCommandService.confirmPayments(List.of(1L, 2L, 3L));

      // then
      assertThat(result.succeededIds()).containsExactly(1L, 3L);
      assertThat(result.failures())
          .containsExactly(
              new AdminBulkResultResponse.Failure(
                  2L,
                  ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID.getCode(),
                  ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID.getMessage()));
    }

    @Test
    void 중복된_참여_ID는_한_번만_처리한다() {
      // given
      willDoNothing().given(participationService).confirmPaymentByAdmin(1L);

      // when
      AdminBulkResultResponse result =
          adminPaymentCommandService.confirmPayments(List.of(1L, 1L, 1L));

      // then
      assertThat(result.succeededIds()).containsExactly(1L);
      assertThat(result.failures()).isEmpty();
      then(participationService).should(times(1)).confirmPaymentByAdmin(1L);
    }

    @Test
    void 예상_밖_예외도_실패_항목으로_수집해_이미_커밋된_성공분을_응답에_보존한다() {
      // given
      willDoNothing().given(participationService).confirmPaymentByAdmin(1L);
      willThrow(new IllegalStateException("connection lost"))
          .given(participationService)
          .confirmPaymentByAdmin(2L);

      // when
      AdminBulkResultResponse result =
          adminPaymentCommandService.confirmPayments(List.of(1L, 2L));

      // then
      assertThat(result.succeededIds()).containsExactly(1L);
      assertThat(result.failures())
          .containsExactly(
              new AdminBulkResultResponse.Failure(
                  2L,
                  ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                  ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
  }
}

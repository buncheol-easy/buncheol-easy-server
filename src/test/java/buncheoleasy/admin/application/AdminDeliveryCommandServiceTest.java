package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.delivery.application.DeliveryService;
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
@DisplayName("AdminDeliveryCommandService 단위 테스트")
class AdminDeliveryCommandServiceTest {

  @InjectMocks private AdminDeliveryCommandService adminDeliveryCommandService;

  @Mock private DeliveryService deliveryService;

  @Nested
  @DisplayName("registerTracking 테스트")
  class RegisterTrackingTest {

    @Test
    void 모든_배송_건에_운송장을_등록한다() {
      // given
      willDoNothing().given(deliveryService).registerTrackingByAdmin(1L, "TRACK-1");
      willDoNothing().given(deliveryService).registerTrackingByAdmin(2L, "TRACK-1");

      // when
      AdminBulkResultResponse result =
          adminDeliveryCommandService.registerTracking(List.of(1L, 2L), "TRACK-1");

      // then
      assertThat(result.succeededIds()).containsExactly(1L, 2L);
      assertThat(result.failures()).isEmpty();
      then(deliveryService).should().registerTrackingByAdmin(1L, "TRACK-1");
      then(deliveryService).should().registerTrackingByAdmin(2L, "TRACK-1");
    }

    @Test
    void 일부_실패해도_나머지_건은_계속_처리하고_실패_사유를_모아_돌려준다() {
      // given
      willThrow(new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID))
          .given(deliveryService)
          .registerTrackingByAdmin(1L, "TRACK-1");
      willDoNothing().given(deliveryService).registerTrackingByAdmin(2L, "TRACK-1");

      // when
      AdminBulkResultResponse result =
          adminDeliveryCommandService.registerTracking(List.of(1L, 2L), "TRACK-1");

      // then
      assertThat(result.succeededIds()).containsExactly(2L);
      assertThat(result.failures())
          .containsExactly(
              new AdminBulkResultResponse.Failure(
                  1L,
                  ErrorCode.DELIVERY_STATE_TRANSITION_INVALID.getCode(),
                  ErrorCode.DELIVERY_STATE_TRANSITION_INVALID.getMessage()));
    }
  }
}

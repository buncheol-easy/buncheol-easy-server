package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 관리자 운송장 등록 벌크 요청. 같은 묶음배송의 여러 배송 건에 동일한 운송장 번호를 한 번에 등록한다. */
public record AdminTrackingRegistrationRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> deliveryIds,
    @NotBlank @Size(max = 100) String trackingNumber) {}

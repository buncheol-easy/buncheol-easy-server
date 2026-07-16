package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 관리자 수령완료 벌크 요청. 여러 배송 건을 한 번에 수령완료로 전이한다. */
public record AdminReceiptConfirmRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> deliveryIds) {}

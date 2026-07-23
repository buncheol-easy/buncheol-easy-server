package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 관리자 입금확인 벌크 요청. 한 참여자가 묶어 입금한 여러 참여를 한 번에 확인한다. */
public record AdminPaymentConfirmRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> participationIds) {}

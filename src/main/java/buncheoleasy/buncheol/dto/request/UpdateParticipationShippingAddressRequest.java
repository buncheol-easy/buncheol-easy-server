package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;

/** 입금대기중 참여의 배송지 변경 요청. 변경할(본인 소유) 배송지 ID 를 받는다. */
public record UpdateParticipationShippingAddressRequest(@NotNull Long shippingAddressId) {}

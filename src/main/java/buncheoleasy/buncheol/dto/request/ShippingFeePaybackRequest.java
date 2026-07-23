package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 배송비 환급(배송비 돌려받기) 신청 요청. 형식 검증·정규화는 PaybackTweetUrl 이 수행한다. */
public record ShippingFeePaybackRequest(@NotBlank String tweetUrl) {}

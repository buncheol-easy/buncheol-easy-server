package buncheoleasy.buncheol.application.payback;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 오픈 이벤트 배송비 환급(배송비 돌려받기) 설정. 이벤트는 개최 기능 오픈 전 한시 운영이라 DB 가 아닌 환경변수로 관리하고, 개최 기능 오픈 시 {@code
 * enabled=false} 로 내려 종료한다 — 이미 저장된 REQUESTED/COMPLETED/REJECTED 는 그대로 유지되고, 미신청 건만 대상에서 빠진다.
 *
 * @param enabled 이벤트 활성 여부. false 면 전 참여가 환급 비대상이다.
 * @param eventStartAt 이벤트 분철 개최(created_at 기준) 판정 구간 시작.
 * @param eventEndAt 이벤트 분철 개최 판정 구간 끝.
 * @param submitWindowDays 신청 마감 산정용 일수. 마감 기준 시점(현재 임시로 배송 완료 시각 — {@link
 *     ShippingFeePaybackPolicy#submitDeadline} 참고)부터 이 일수가 지나면 신청이 만료(EXPIRED)된다. 분철·참여마다 기준 시점이 달라
 *     전역 고정 마감일이 아닌 상대 일수로 관리한다.
 */
@Validated
@ConfigurationProperties(prefix = "shipping-fee-payback")
public record ShippingFeePaybackProperties(
    boolean enabled,
    @NotNull Instant eventStartAt,
    @NotNull Instant eventEndAt,
    @Positive int submitWindowDays) {

  /** 분철 개최 시각이 이벤트 구간 안인지 (경계 포함). */
  public boolean isHostedInEventPeriod(final Instant hostedAt) {
    return !hostedAt.isBefore(eventStartAt) && !hostedAt.isAfter(eventEndAt);
  }
}

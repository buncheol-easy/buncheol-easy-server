package buncheoleasy.buncheol.application.payback;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 오픈 이벤트 배송비 환급(배송비 돌려받기) 설정. 이벤트는 개최 기능 오픈 전 한시 운영이라 DB 가 아닌 환경변수로 관리하고, 종료 시 {@code
 * enabled=false} 로 내린다 — 이미 저장된 REQUESTED/COMPLETED/REJECTED 는 그대로 유지되고, 미신청 건만 대상에서 빠진다.
 *
 * <p>0원 슬롯 분철은 운영진(개발자)만 발행하므로 별도의 이벤트 기간 판정은 두지 않는다 — 대상 여부는 "이벤트 활성 + 0원 슬롯"이 전부다.
 *
 * @param enabled 이벤트 활성 여부. false 면 전 참여가 환급 비대상이다.
 * @param submitWindowDays 신청 마감 산정용 일수. 마감 기준 시점(현재 임시로 배송 완료 시각 — {@link
 *     ShippingFeePaybackPolicy} 참고)부터 이 일수가 지나면 신청이 만료(EXPIRED)된다.
 */
@Validated
@ConfigurationProperties(prefix = "shipping-fee-payback")
public record ShippingFeePaybackProperties(boolean enabled, @Positive int submitWindowDays) {}

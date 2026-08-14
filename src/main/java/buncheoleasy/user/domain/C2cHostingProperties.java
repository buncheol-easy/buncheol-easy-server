package buncheoleasy.user.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 일반 회원의 C2C 개최 오픈 여부. 코드는 prod 에 먼저 배포하고 홍보 시점에 스위치만 올리기 위한 것으로, 배포 없이 환경변수로 전환한다 ({@code
 * ShippingFeePaybackProperties} 와 같은 컨벤션).
 *
 * <p><b>기본값이 {@code false} 인 이유</b>: 주입을 빠뜨렸을 때의 두 실패가 대칭이 아니다. 켜져야 하는데 꺼져 있으면 개최 폼에 안내가 뜨고 바로
 * 드러나지만, 꺼져야 하는데 켜져 있으면 <b>홍보 전에 서비스가 조용히 열린다</b>. 되돌릴 수 없는 쪽을 기본값으로 두지 않는다.
 *
 * <p>⚠️ 이 값은 <b>일반 회원의 개최 경로에만</b> 걸린다. 운영진({@code can_host})의 개최는 {@code
 * BuncheolService#resolveHostFlowType} 에서 자격 게이트를 타지 않으므로 꺼져 있어도 계속 열 수 있다 — prod 최종 점검을 운영진
 * 계정으로 하기 위한 것이다.
 *
 * <p>⚠️ <b>막는 것은 "여는 것" 뿐이다.</b> 공개 목록 조회에는 {@code flowType} 필터가 없고 참여 경로에도 이 게이트가 없으므로,
 * 점검용으로 연 C2C 분철은 <b>스위치가 꺼져 있어도 실사용자에게 카드로 노출되고 신청까지 들어온다.</b> 홍보 전 prod 점검은 확인 후
 * 바로 취소하거나, 노출을 감수할 수 없다면 별도 수단으로 한다.
 *
 * @param enabled 일반 회원의 C2C 개최 허용 여부. false 면 자격을 모두 갖춰도 {@link
 *     C2cHostQualification#NOT_OPEN_YET} 으로 막힌다.
 */
@ConfigurationProperties(prefix = "c2c-hosting")
public record C2cHostingProperties(boolean enabled) {}

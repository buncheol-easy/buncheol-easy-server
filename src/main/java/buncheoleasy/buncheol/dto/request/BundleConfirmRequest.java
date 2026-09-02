package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 묶음 입금확인 요청.
 *
 * <p>🔴 {@code expectedSlotIds} 는 <b>필수</b>이고, 개최 관리 응답에서 {@code confirmTarget} 이 {@code true}
 * 인 슬롯만 담아야 한다 (docs/70 결정 23). <b>취소분·확정분까지 담으면 영원히 409</b> 가 난다 — 서버는 확인
 * 가능 상태의 슬롯 집합과 대조하기 때문이다.
 *
 * <p>이렇게 서버가 대상 판정을 응답에 실어 주므로 화면은 상태 문자열을 해석하지 않아도 되고, 확인 가능 상태가
 * 늘어도 양쪽이 조용히 갈리지 않는다 — 「제외」가 {@code releasability} 로 한 것과 같은 처방이다.
 *
 * <p>대조하는 이유는 개최자가 <b>보지 못한 슬롯까지 확정</b>되지 않게 하기 위함이다. 추가 모집으로 생긴 묶음은
 * 슬롯이 늘거나 줄 수 있다(그쪽은 개별 취소가 열려 있다). 순서는 보지 않고 집합으로 대조한다.
 *
 * <p>⚠️ {@code @NotEmpty} 는 <b>원소</b>를 검증하지 않는다 — {@code List<@NotNull Long>} 이 없으면
 * {@code [232, null]} 이 통과해 서비스에서 NPE 로 500 이 된다(400 이어야 할 입력 오류다).
 */
public record BundleConfirmRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> expectedSlotIds) {}

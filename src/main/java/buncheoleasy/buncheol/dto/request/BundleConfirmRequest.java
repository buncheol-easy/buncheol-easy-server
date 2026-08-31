package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 묶음 입금확인 요청.
 *
 * <p>🔴 {@code expectedSlotIds} 는 <b>필수</b>다 (docs/70 결정 23). 개최자가 화면에서 본 슬롯 집합을 그대로
 * 실어 보내면, 서버의 실제 집합과 다를 때 409 로 막고 새로고침을 유도한다 — 추가 모집으로 생긴 묶음은 슬롯이
 * 늘거나 줄 수 있어(그쪽은 개별 취소가 열려 있다) <b>개최자가 보지 못한 슬롯까지 확정</b>되면 안 된다.
 */
public record BundleConfirmRequest(@NotEmpty List<Long> expectedSlotIds) {}

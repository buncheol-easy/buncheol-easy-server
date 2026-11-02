package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 추가 신청 수락·거절 요청. 입금 확인({@link BundleConfirmRequest})과 같은 모양이라 화면이 대조 코드를 같이 쓴다.
 *
 * <p>{@code expectedSlotIds} 에는 개최 관리 응답에서 {@code acceptTarget} 이 {@code true} 인 그 묶음의 슬롯만 담는다. 서버의
 * 수락 전 슬롯 집합과 다르면 409(BCH-115)다 — 개최자가 보지 못한 슬롯까지 수락·거절되면 안 된다.
 */
public record BundleSlotsRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> expectedSlotIds) {}

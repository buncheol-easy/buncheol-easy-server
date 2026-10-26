package buncheoleasy.aidraft.dto.response;

/**
 * AI 작성 남은 횟수. 기능이 꺼져 있어도 200 으로 {@code enabled=false} 를 준다 — 화면이 AI 칸을 숨기는 신호다.
 *
 * @param enabled 기능을 쓸 수 있는지
 * @param dailyLimit 1인 하루 한도
 * @param remainingToday 오늘 남은 횟수. 꺼져 있으면 0
 */
public record AiDraftQuotaResponse(boolean enabled, int dailyLimit, int remainingToday) {}

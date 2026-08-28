package buncheoleasy.buncheol.dto.request;

/**
 * 구버전 클라이언트가 참여 요청에 실어 보내던 환불 계좌. 서버는 이 값을 쓰지 않고 마이페이지 정산 계좌를 읽는다 — {@link
 * ParticipateRequest#refundAccount()} 참고. 클라이언트 배포 후 필드와 함께 제거한다.
 */
public record RefundAccountRequest(String bank, String account, String holder) {}

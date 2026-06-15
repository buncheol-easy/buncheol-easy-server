package buncheoleasy.notification.infrastructure;

import buncheoleasy.notification.domain.AlimtalkButton;

/**
 * 알림톡 단건 발송 요청. {@code message} 는 승인된 템플릿 본문과 개행까지 동일해야 하며(변수 치환 완료본), {@code button} 은 버튼이 없으면 null
 * 이다(이미 링크 변수까지 치환된 상태).
 */
public record AlimtalkSendRequest(
    String tplCode, String receiverPhone, String subject, String message, AlimtalkButton button) {}

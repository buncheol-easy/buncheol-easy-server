package buncheoleasy.feedback.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 의견 보내기 요청. 답장 없는 단방향 수집이라 연락처는 받지 않는다(받으면 회신 의무가 생긴다).
 *
 * @param content 의견 본문. 슬랙 메시지에 그대로 실린다.
 * @param screenPath 의견을 남긴 화면의 in-app 경로(선택). 어디서 막혔는지가 본문만큼 중요해 함께 받는다.
 */
public record CreateFeedbackRequest(
    @NotBlank @Size(max = 500) String content,
    // 공지 linkPath 와 동일한 방어: in-app 상대 경로만 허용해 외부 URL 이 슬랙 메시지에 실리지 않게 한다.
    // `.` 은 개행에 매칭되지 않아 경로에 줄바꿈이 섞이면 거부된다 — 슬랙 주입 방어 관점에서 의도한 동작이다.
    // message 는 생략한다: GlobalExceptionHandler 가 검증 실패를 ErrorCode.INVALID_INPUT_VALUE 로
    // 일괄 변환하므로 개별 message 는 사용자에게 도달하지 않는다.
    @Size(max = 200) @Pattern(regexp = "^/(?![/\\\\]).*") String screenPath) {}

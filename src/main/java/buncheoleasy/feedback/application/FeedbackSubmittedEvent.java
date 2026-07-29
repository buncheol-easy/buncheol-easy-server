package buncheoleasy.feedback.application;

/**
 * 사용자 의견 접수 이벤트. 저장하지 않고 슬랙으로만 흘려보내므로 필요한 표시 정보를 그대로 담는다.
 *
 * @param content 의견 본문
 * @param screenPath 의견을 남긴 화면 경로 (없을 수 있음)
 * @param userId 로그인 회원 ID (비로그인이면 null)
 * @param nickname 로그인 회원 닉네임 (비로그인이거나 조회 실패면 null)
 */
public record FeedbackSubmittedEvent(
    String content, String screenPath, Long userId, String nickname) {}

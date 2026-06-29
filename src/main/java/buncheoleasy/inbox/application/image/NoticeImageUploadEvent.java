package buncheoleasy.inbox.application.image;

/**
 * 공지 이미지 업로드 이벤트. 공지 커밋 후 비동기 S3 업로드 트리거로, 본문 이미지({@code image})와 홈 배너({@code bannerTitle} +
 * {@code bannerImage})를 함께 싣는다. 한 공지의 이미지/배너를 단일 이벤트·단일 트랜잭션에서 반영해, 같은 row 를 동시에 갱신하다 한쪽이 덮이는
 * 경합(lost-update)을 구조적으로 차단한다. 각 필드는 첨부 없으면 null.
 */
public record NoticeImageUploadEvent(
    Long noticeId, ImageFile image, String bannerTitle, ImageFile bannerImage) {}

package buncheoleasy.inbox.domain;

import buncheoleasy.global.page.Cursor;
import java.util.List;
import java.util.Optional;

public interface InboxMessageRepository {

  InboxMessage save(InboxMessage message);

  Optional<InboxMessage> findById(Long id);

  /**
   * 수신함 본문 피드(고정 제외). 공지와 {@code userId} 본인 알림 중 필터에 해당하는 것을 {@code createdAt DESC, id DESC} 로
   * 조회한다. hasNext 판별을 위해 호출 측에서 {@code limit = size + 1} 을 넘긴다.
   */
  List<InboxMessage> findFeed(
      Long userId, boolean includeNotices, boolean includeNotifications, Cursor cursor, int limit);

  /** 상단 고정 공지(개수 적음). {@code createdAt DESC, id DESC} 정렬. 첫 페이지 prepend 용. */
  List<InboxMessage> findPinnedNotices();

  /** {@code userId} 가 볼 수 있는(공지이거나 본인 알림) 메시지만 단건 조회한다. */
  Optional<InboxMessage> findVisibleById(Long id, Long userId);

  /** 홈 배너가 등록된(banner_image_url 채워진) 공지를 전체 조회한다. {@code id DESC}(최신순), 개수 제한 없음. */
  List<InboxMessage> findBanners();
}

package buncheoleasy.inbox.application;

import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.dto.response.BannerResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 배너 조회. 배너가 등록된(banner_image_url 채워진) 공지를 최신순으로 전체 반환한다(개수 제한 없음). */
@Service
@RequiredArgsConstructor
public class BannerQueryService {

  private final InboxMessageRepository inboxMessageRepository;

  @Transactional(readOnly = true)
  public List<BannerResponse> getBanners() {
    return inboxMessageRepository.findBanners().stream().map(BannerResponse::from).toList();
  }
}

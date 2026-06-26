package buncheoleasy.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.dto.response.BannerResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("BannerQueryService 테스트")
class BannerQueryServiceTest {

  @Mock private InboxMessageRepository inboxMessageRepository;

  @InjectMocks private BannerQueryService bannerQueryService;

  @Test
  void 배너_등록_공지를_BannerResponse로_매핑해_반환한다() {
    InboxMessage notice = InboxMessage.createNotice("공지", null, "설명", false, null);
    ReflectionTestUtils.setField(notice, "id", 8L);
    notice.attachBanner("여름 이벤트", "https://cdn.example.com/b.jpg");
    given(inboxMessageRepository.findBanners()).willReturn(List.of(notice));

    List<BannerResponse> result = bannerQueryService.getBanners();

    assertThat(result).hasSize(1);
    // noticeId 는 공지 상세로 이동하는 키라 매핑을 명시 검증한다.
    assertThat(result.getFirst().noticeId()).isEqualTo(8L);
    assertThat(result.getFirst().bannerTitle()).isEqualTo("여름 이벤트");
    assertThat(result.getFirst().bannerImageUrl()).isEqualTo("https://cdn.example.com/b.jpg");
  }

  @Test
  void 배너가_없으면_빈_목록을_반환한다() {
    given(inboxMessageRepository.findBanners()).willReturn(List.of());

    assertThat(bannerQueryService.getBanners()).isEmpty();
  }
}

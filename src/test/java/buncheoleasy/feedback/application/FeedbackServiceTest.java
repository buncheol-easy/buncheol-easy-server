package buncheoleasy.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.feedback.domain.FeedbackRateLimiter;
import buncheoleasy.feedback.dto.request.CreateFeedbackRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("FeedbackService 테스트")
class FeedbackServiceTest {

  private FeedbackRateLimiter rateLimiter;
  private UserRepository userRepository;
  private ApplicationEventPublisher eventPublisher;
  private FeedbackService feedbackService;

  @BeforeEach
  void setUp() {
    rateLimiter = mock(FeedbackRateLimiter.class);
    userRepository = mock(UserRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    feedbackService = new FeedbackService(rateLimiter, userRepository, eventPublisher);
  }

  private CreateFeedbackRequest request() {
    return new CreateFeedbackRequest("입금 계좌를 못 찾겠어요", "/profile/bids");
  }

  private FeedbackSubmittedEvent capturedEvent() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return (FeedbackSubmittedEvent) captor.getValue();
  }

  @Test
  void 로그인_회원은_회원_ID_로_도배_제한한다() {
    // IP 를 공유하는 환경(학교·카페 와이파이)에서 서로를 막지 않도록 회원은 회원 ID 가 키다.
    User user = mock(User.class);
    given(user.getNickname()).willReturn(new Nickname("분철러123"));
    given(userRepository.findById(7L)).willReturn(Optional.of(user));

    feedbackService.submit(7L, "1.2.3.4", request());

    verify(rateLimiter).checkAndRecord("user:7");
    assertThat(capturedEvent().nickname()).isEqualTo("분철러123");
    assertThat(capturedEvent().userId()).isEqualTo(7L);
  }

  @Test
  void 비로그인은_IP_로_도배_제한하고_닉네임_없이_발행한다() {
    feedbackService.submit(null, "1.2.3.4", request());

    verify(rateLimiter).checkAndRecord("ip:1.2.3.4");
    assertThat(capturedEvent().userId()).isNull();
    assertThat(capturedEvent().nickname()).isNull();
  }

  @Test
  void 회원이_조회되지_않아도_접수는_성공한다() {
    // 닉네임은 표시용 부가 정보라 조회 실패가 접수를 막으면 안 된다.
    given(userRepository.findById(7L)).willReturn(Optional.empty());

    feedbackService.submit(7L, "1.2.3.4", request());

    assertThat(capturedEvent().nickname()).isNull();
    assertThat(capturedEvent().userId()).isEqualTo(7L);
  }

  @Test
  void 도배_한도를_넘으면_이벤트를_발행하지_않는다() {
    willThrow(new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED))
        .given(rateLimiter)
        .checkAndRecord("ip:1.2.3.4");

    assertThatThrownBy(() -> feedbackService.submit(null, "1.2.3.4", request()))
        .isInstanceOf(BusinessException.class);

    verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
  }

  @Test
  void 화면_경로가_없으면_null_로_발행된다() {
    feedbackService.submit(null, "1.2.3.4", new CreateFeedbackRequest("그냥 좋아요", null));

    assertThat(capturedEvent().screenPath()).isNull();
    assertThat(capturedEvent().content()).isEqualTo("그냥 좋아요");
  }
}

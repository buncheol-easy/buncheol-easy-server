package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolService 단위 테스트")
class BuncheolServiceTest {

  private static final Long HOST_ID = 1L;
  private static final Long BUNCHEOL_ID = 10L;
  private static final Long GROUP_ID = 100L;
  private static final Long MEMBER_ID = 200L;
  private static final String MEMBER_NAME = "멤버A";
  private static final String MEMBER_IMAGE = "https://cdn.example.com/members/200.jpg";
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

  @InjectMocks private BuncheolService buncheolService;

  @Mock private BuncheolDomainService buncheolDomainService;

  @Mock private BuncheolImageDomainService buncheolImageDomainService;

  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;

  @Mock private ParticipationDomainService participationDomainService;

  @Mock private DeliveryDomainService deliveryDomainService;

  @Mock private GroupDomainService groupDomainService;

  @Mock private UserDomainService userDomainService;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Captor private ArgumentCaptor<BuncheolParams> buncheolParamsCaptor;

  @Captor private ArgumentCaptor<List<BuncheolMemberParams>> buncheolMemberParamsCaptor;

  @Captor private ArgumentCaptor<List<Long>> keepImageIdsCaptor;

  @Captor private ArgumentCaptor<BuncheolImageUploadEvent> imageUploadEventCaptor;

  private static GroupMember groupMember(Long memberId) {
    return new GroupMember(memberId, GROUP_ID, MEMBER_NAME, MEMBER_IMAGE);
  }

  // 대표사진 지정은 필수(DTO @NotNull)라 기본 픽스처는 첫 번째 이미지를 지정한다.
  private HoldBuncheolRequest holdRequest(List<BuncheolMemberRequest> members) {
    return holdRequest(members, 0);
  }

  private HoldBuncheolRequest holdRequest(
      List<BuncheolMemberRequest> members, Integer thumbnailIndex) {
    return new HoldBuncheolRequest(
        GROUP_ID,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 스토어",
        Instant.now().plus(7, ChronoUnit.DAYS),
        3,
        3000,
        null,
        null,
        null,
        thumbnailIndex,
        members);
  }

  private HoldBuncheolRequest holdRequestWithFlow(
      List<BuncheolMemberRequest> members, FlowType flowType) {
    return new HoldBuncheolRequest(
        GROUP_ID,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 스토어",
        Instant.now().plus(7, ChronoUnit.DAYS),
        3,
        3000,
        null,
        null,
        flowType,
        0,
        members);
  }

  // 대표사진 지정은 필수라 기본 픽스처는 신규 이미지 0번을 지정한다.
  private BuncheolModifyRequest modifyRequest() {
    return new BuncheolModifyRequest("수정 분철 제목", "수정 설명", List.of(), null, 0, null);
  }

  @Nested
  @DisplayName("분철 개최 테스트")
  class HoldBuncheolTest {

    @Test
    void 분철_개최에_성공하고_분철_및_분철_멤버가_저장된다() {
      // given — 분철은 이미지 최소 1장이 필수이므로 정상 개최는 이미지 1장 이상으로 호출된다.
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

      // when
      Long createdId = buncheolService.holdBuncheol(HOST_ID, request, images);

      // then
      // 생성된 분철의 id 를 그대로 돌려줘야 FE 가 목록 재조회·제목 매칭 없이 이동할 수 있다 (docs/53 Q-15).
      assertThat(createdId).isEqualTo(BUNCHEOL_ID);
      then(groupDomainService).should().validateGroupExists(GROUP_ID);
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(HOST_ID), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(BUNCHEOL_ID), buncheolMemberParamsCaptor.capture());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isEqualTo(GROUP_ID);

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isEqualTo(MEMBER_ID);
      assertThat(memberParams.getFirst().price()).isEqualTo(50_000L);
    }

    @Test
    void 이미지가_0장이면_BUNCHEOL_IMAGE_REQUIRED_예외가_발생하고_분철이_저장되지_않는다() {
      // given — 이미지 개수 검증이 가장 먼저 수행되어 0장이면 즉시 차단된다.
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_REQUIRED))
          .given(buncheolImageDomainService)
          .validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_REQUIRED);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 이미지가_있는_경우_이미지_업로드_이벤트가_발행된다() {
      // given
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

      // when
      buncheolService.holdBuncheol(HOST_ID, request, images);

      // then
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
    }

    @Test
    void thumbnailIndex를_지정하면_업로드_이벤트에_그대로_전달된다() {
      // given
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)), 1);
      List<ImageFile> images =
          List.of(
              new ImageFile("image1.jpg", "image/jpeg", new byte[] {1}),
              new ImageFile("image2.jpg", "image/jpeg", new byte[] {2}));

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);

      // when
      buncheolService.holdBuncheol(HOST_ID, request, images);

      // then
      then(buncheolImageDomainService).should().validateThumbnailIndex(2, 1);
      then(eventPublisher).should().publishEvent(imageUploadEventCaptor.capture());
      BuncheolImageUploadEvent event = imageUploadEventCaptor.getValue();
      assertThat(event.buncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(event.thumbnailIndex()).isEqualTo(1);
    }

    @Test
    void thumbnailIndex가_이미지_범위를_벗어나면_예외가_발생하고_분철이_저장되지_않는다() {
      // given
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)), 5);
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

      willThrow(new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_INDEX_INVALID))
          .given(buncheolImageDomainService)
          .validateThumbnailIndex(1, 5);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_INDEX_INVALID);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 이미지_개수가_초과되면_예외가_발생한다() {
      // given
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));
      List<ImageFile> images =
          List.of(
              new ImageFile("image1.jpg", "image/jpeg", new byte[] {1}),
              new ImageFile("image2.jpg", "image/jpeg", new byte[] {2}),
              new ImageFile("image3.jpg", "image/jpeg", new byte[] {3}),
              new ImageFile("image4.jpg", "image/jpeg", new byte[] {4}),
              new ImageFile("image5.jpg", "image/jpeg", new byte[] {5}),
              new ImageFile("image6.jpg", "image/jpeg", new byte[] {6}));

      willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED))
          .given(buncheolImageDomainService)
          .validateImageCount(6);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    void 멤버_ID가_중복되면_예외가_발생한다() {
      // given
      HoldBuncheolRequest request =
          holdRequest(
              List.of(
                  new BuncheolMemberRequest(MEMBER_ID, 50_000L),
                  new BuncheolMemberRequest(MEMBER_ID, 30_000L)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }

    @Test
    void 존재하지_않는_그룹ID이면_예외가_발생한다() {
      // given
      willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND))
          .given(groupDomainService)
          .validateGroupExists(GROUP_ID);

      HoldBuncheolRequest request = holdRequest(List.of(new BuncheolMemberRequest(1L, 50_000L)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 일반_유저는_자격_게이트를_거쳐_C2C로_강제된다() {
      // given — canHost=false(기본 목 동작)인 일반 유저
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);

      // when
      buncheolService.holdBuncheol(HOST_ID, request, images);

      // then
      then(userDomainService).should().requireC2cHostQualification(HOST_ID);
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(HOST_ID), buncheolParamsCaptor.capture());
      assertThat(buncheolParamsCaptor.getValue().flowType()).isEqualTo(FlowType.C2C);
    }

    @Test
    void 일반_유저가_LEGACY를_요청하면_USER_CANNOT_HOST_예외가_발생한다() {
      // given — LEGACY 는 페이액션·운영 절차가 붙는 운영진 전용 방식이라 일반 유저 요청은 거부한다.
      HoldBuncheolRequest request =
          holdRequestWithFlow(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)), FlowType.LEGACY);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_CANNOT_HOST);

      then(userDomainService).should(never()).requireC2cHostQualification(any());
      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
    }

    @Test
    void 운영진은_기본_LEGACY로_개최되고_자격_게이트를_타지_않는다() {
      // given
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);

      // when
      buncheolService.holdBuncheol(HOST_ID, request, images);

      // then
      then(userDomainService).should(never()).requireC2cHostQualification(any());
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(HOST_ID), buncheolParamsCaptor.capture());
      assertThat(buncheolParamsCaptor.getValue().flowType()).isEqualTo(FlowType.LEGACY);
    }

    @Test
    void 운영진은_C2C_개최를_선택할_수_있다() {
      // given
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      HoldBuncheolRequest request =
          holdRequestWithFlow(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)), FlowType.C2C);
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);

      // when
      buncheolService.holdBuncheol(HOST_ID, request, images);

      // then
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(HOST_ID), buncheolParamsCaptor.capture());
      assertThat(buncheolParamsCaptor.getValue().flowType()).isEqualTo(FlowType.C2C);
    }

    @Test
    void 일반_유저는_활성_개최_상한을_넘으면_예외가_발생한다() {
      // given — 자격 게이트(목 no-op) 통과 후 상한 검증에서 차단되는 경우
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED))
          .given(buncheolDomainService)
          .validateActiveHostedLimit(HOST_ID);
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
    }

    @Test
    void 운영진의_C2C_선택도_가입_완료는_요구한다() {
      // given — 성인 확인은 건너뛰지만 연락처(분쟁 처리 근거)는 운영진에게도 요구한다.
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      willThrow(new BusinessException(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE))
          .given(userDomainService)
          .requireProfileCompleted(HOST_ID);
      HoldBuncheolRequest request =
          holdRequestWithFlow(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)), FlowType.C2C);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
    }

    @Test
    void 자격_게이트에_걸리면_분철이_저장되지_않는다() {
      // given — 연령대 미확인(USR-032)이 게이트에서 던져지는 경우
      willThrow(new BusinessException(ErrorCode.USER_AGE_NOT_VERIFIED))
          .given(userDomainService)
          .requireC2cHostQualification(HOST_ID);
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_AGE_NOT_VERIFIED);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
    }

    @Test
    void 호스트가_정산_계좌를_등록하지_않았으면_예외가_발생한다() {
      // given
      willThrow(new BusinessException(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED))
          .given(userDomainService)
          .requireBankAccountRegistered(HOST_ID);

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }
  }

  @Nested
  @DisplayName("분철 수정 테스트")
  class ModifyBuncheolTest {

    @Test
    void 제목과_설명만_갱신되고_이미지는_keepImageIds_기준으로_정리된다() {
      // given
      BuncheolModifyRequest request =
          new BuncheolModifyRequest("수정 제목", "수정 설명", List.of(1L, 2L), 1L, null, null);
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing()
          .given(buncheolImageDomainService)
          .validateModifyImageCount(BUNCHEOL_ID, List.of(1L, 2L), 0);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheol).should().validateRecruiting(any(Instant.class));
      then(buncheolDomainService).should().updateBuncheolContent(buncheol, "수정 제목", "수정 설명");
      then(buncheolImageDomainService)
          .should()
          .deleteImagesExcluding(eq(BUNCHEOL_ID), keepImageIdsCaptor.capture());
      assertThat(keepImageIdsCaptor.getValue()).containsExactly(1L, 2L);
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 수정시_새_이미지가_있으면_업로드_이벤트를_발행한다() {
      // given
      BuncheolModifyRequest request = modifyRequest();
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing()
          .given(buncheolImageDomainService)
          .validateModifyImageCount(BUNCHEOL_ID, List.of(), 1);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images);

      // then
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
    }

    @Test
    void 오픈채팅_링크_수정이_도메인_서비스로_위임된다() {
      // given — 링크 값의 유지/제거/검증 계약은 엔티티(updateOpenChatUrl)가 책임진다.
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              "수정 제목", "수정 설명", List.of(1L, 2L), 1L, null, "https://open.kakao.com/o/gAbCdEf");
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheolDomainService)
          .should()
          .updateBuncheolOpenChatUrl(buncheol, "https://open.kakao.com/o/gAbCdEf");
    }

    @Test
    void thumbnailImageId를_지정하면_기존_이미지로_대표사진을_교체한다() {
      // given
      BuncheolModifyRequest request =
          new BuncheolModifyRequest("수정 제목", "수정 설명", List.of(1L, 2L), 2L, null, null);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheolImageDomainService)
          .should()
          .validateThumbnailSelection(List.of(1L, 2L), 0, 2L, null);
      then(buncheolImageDomainService).should().changeThumbnail(BUNCHEOL_ID, 2L);
      then(buncheolImageDomainService).should(never()).clearThumbnail(anyLong());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void thumbnailIndex를_지정하면_기존_플래그를_해제하고_이벤트에_인덱스를_전달한다() {
      // given — 신규 업로드 이미지가 대표가 될 예정이므로 기존 플래그만 해제하고, 지정은 커밋 후 리스너가 수행한다.
      BuncheolModifyRequest request =
          new BuncheolModifyRequest("수정 제목", "수정 설명", List.of(1L), null, 0, null);
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images);

      // then
      then(buncheolImageDomainService).should().clearThumbnail(BUNCHEOL_ID);
      then(buncheolImageDomainService).should(never()).changeThumbnail(anyLong(), anyLong());
      then(eventPublisher).should().publishEvent(imageUploadEventCaptor.capture());
      BuncheolImageUploadEvent event = imageUploadEventCaptor.getValue();
      assertThat(event.buncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(event.thumbnailIndex()).isZero();
    }

    @Test
    void 대표사진을_지정하지_않으면_예외가_발생하고_이미지_변경이_진행되지_않는다() {
      // given — 대표사진 지정은 필수(둘 중 정확히 하나)다.
      BuncheolModifyRequest request =
          new BuncheolModifyRequest("수정 제목", "수정 설명", List.of(1L), null, null, null);
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_REQUIRED))
          .given(buncheolImageDomainService)
          .validateThumbnailSelection(List.of(1L), 1, null, null);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_REQUIRED);

      then(buncheolImageDomainService).should(never()).deleteImagesExcluding(anyLong(), anyList());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 수정시_이미지_개수_초과면_예외가_발생한다() {
      // given
      BuncheolModifyRequest request = modifyRequest();
      List<ImageFile> images =
          List.of(
              new ImageFile("1.jpg", "image/jpeg", new byte[] {1}),
              new ImageFile("2.jpg", "image/jpeg", new byte[] {2}),
              new ImageFile("3.jpg", "image/jpeg", new byte[] {3}),
              new ImageFile("4.jpg", "image/jpeg", new byte[] {4}),
              new ImageFile("5.jpg", "image/jpeg", new byte[] {5}),
              new ImageFile("6.jpg", "image/jpeg", new byte[] {6}));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED))
          .given(buncheolImageDomainService)
          .validateModifyImageCount(BUNCHEOL_ID, List.of(), 6);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);

      then(buncheolDomainService).should(never()).updateBuncheolContent(any(), any(), any());
    }

    @Test
    void 분철이_없으면_수정에_실패한다() {
      // given
      Long buncheolId = 999L;
      BuncheolModifyRequest request = modifyRequest();
      given(buncheolDomainService.getBuncheol(buncheolId))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }

    @Test
    void 소유자가_아니면_수정에_실패하고_업데이트를_진행하지_않는다() {
      // given
      BuncheolModifyRequest request = modifyRequest();
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(HOST_ID);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolDomainService).should(never()).updateBuncheolContent(any(), any(), any());
      then(buncheolImageDomainService).should(never()).deleteImagesExcluding(anyLong(), anyList());
    }

    @Test
    void 모집중이_아닌_분철은_수정에_실패한다() {
      // given
      BuncheolModifyRequest request = modifyRequest();
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheol)
          .validateRecruiting(any(Instant.class));

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolDomainService).should(never()).updateBuncheolContent(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("분철 취소 테스트")
  class CancelBuncheolTest {

    @Test
    void 모집중_분철_취소에_성공하고_활성_참여도_일괄_CANCELLED_되며_알림_이벤트가_발행된다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .willReturn(BuncheolStatus.RECRUITING);
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(50L);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(participation));

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheolDomainService).should().cancelBuncheol(BUNCHEOL_ID, NOW);
      then(participationDomainService).should().cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
      // 취소된 참여의 배송 스냅샷을 정리한다.
      then(deliveryDomainService).should().deleteByParticipationIds(List.of(50L));
      then(eventPublisher).should().publishEvent(any(BuncheolCancelledEvent.class));
    }

    @Test
    void 인원미달_자동취소_분철_취소시_참여_케스케이드와_알림이_생략된다() {
      // given — 자동취소 시 마감 스케줄러가 참여 취소·배송 정리·알림을 이미 끝냈으므로 재실행하면 취소 알림이 중복 발송된다.
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .willReturn(BuncheolStatus.CANCELLED);

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheolDomainService).should().cancelBuncheol(BUNCHEOL_ID, NOW);
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
      then(participationDomainService).should(never()).findCascadeCancelledByBuncheolId(anyLong());
      then(deliveryDomainService).should(never()).deleteByParticipationIds(anyList());
      then(eventPublisher).should(never()).publishEvent(any(BuncheolCancelledEvent.class));
    }

    @Test
    void 소유자가_아니면_취소에_실패한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(HOST_ID);

      // when & then
      assertThatThrownBy(() -> buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolDomainService).should(never()).cancelBuncheol(anyLong(), any());
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void 분철이_없으면_취소에_실패한다() {
      // given
      Long buncheolId = 999L;
      given(buncheolDomainService.getBuncheol(buncheolId))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      // when & then
      assertThatThrownBy(() -> buncheolService.cancelBuncheol(HOST_ID, buncheolId))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);

      then(buncheolDomainService).should(never()).cancelBuncheol(anyLong(), any());
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void 분철_상태가_취소_불가면_참여_cascade가_호출되지_않는다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED))
          .given(buncheolDomainService)
          .cancelBuncheol(BUNCHEOL_ID, NOW);

      // when & then
      assertThatThrownBy(() -> buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);

      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheolDomainService).should().cancelBuncheol(BUNCHEOL_ID, NOW);
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }
  }

  @Nested
  @DisplayName("진행 확정(finalize-collected) 에러 코드 테스트 (docs/54 4-1)")
  class FinalizeCollectedErrorCodeTest {

    // 성사 확정(BCH-085)과 코드를 공유하면 개최자가 "진행 확정"을 눌렀는데 "성사 확정을 할 수
    // 없습니다"가 뜬다. 코드가 다시 합쳐지면 이 테스트가 잡는다.
    @Test
    void 수집_종료_실패는_성사확정과_다른_전용_코드를_던진다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheolDomainService.confirmIfAllCollected(BUNCHEOL_ID, NOW)).willReturn(false);

      assertThatThrownBy(() -> buncheolService.finalizeCollected(HOST_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_COLLECT_FINALIZE_NOT_ALLOWED);
    }

    // BCH-084 는 성사 확정·진행 확정·반려·보냈어요 등이 공유하는 범용 가드라, 특정 액션 전용
    // 문구(예: 취소 안내)를 여기에 넣으면 다른 액션에서 엉뚱한 안내가 나간다.
    @Test
    void LEGACY_분철의_진행_확정은_범용_플로우_가드로_막힌다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(false);

      assertThatThrownBy(() -> buncheolService.finalizeCollected(HOST_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
  }
}

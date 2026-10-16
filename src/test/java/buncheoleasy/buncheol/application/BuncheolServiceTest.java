package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.participation.ParticipationService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.BuncheolTerms;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSettingDomainService;
import buncheoleasy.buncheol.dto.request.BuncheolMemberAddRequest;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.buncheol.dto.request.HoldStaffBuncheolRequest;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.C2cHostQualification;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolService 단위 테스트")
class BuncheolServiceTest {

  private static final Long HOST_ID = 1L;
  private static final Long OTHER_USER_ID = 2L;
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
  @Mock private ParticipationBundleDomainService participationBundleDomainService;

  @Mock private DeliveryDomainService deliveryDomainService;

  @Mock private GroupDomainService groupDomainService;

  @Mock private UserDomainService userDomainService;

  @Mock private StaffBuncheolSettingDomainService staffBuncheolSettingDomainService;

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
        1,
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
        1,
        3000,
        null,
        null,
        flowType,
        0,
        members);
  }

  private HoldBuncheolRequest holdRequestWithMinHeadcount(
      List<BuncheolMemberRequest> members, int minHeadcount) {
    return new HoldBuncheolRequest(
        GROUP_ID,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 스토어",
        Instant.now().plus(7, ChronoUnit.DAYS),
        minHeadcount,
        3000,
        null,
        null,
        null,
        0,
        members);
  }

  private static List<BuncheolMemberRequest> threeMembers() {
    return List.of(
        new BuncheolMemberRequest(MEMBER_ID, 50_000L),
        new BuncheolMemberRequest(MEMBER_ID + 1, 50_000L),
        new BuncheolMemberRequest(MEMBER_ID + 2, 50_000L));
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

    // CLOSED 는 개최자 「빼기」 전용 값 — 개최 요청으로 만들면 LEGACY 에도 닫힌 자리가 생기고 롤백 대비 전제가 깨진다.
    @Test
    void C2C_개최_요청에_닫힌_자리가_있으면_거부하고_자리를_만들지_않는다() {
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
      HoldBuncheolRequest request =
          holdRequest(
              List.of(
                  new BuncheolMemberRequest(
                      MEMBER_ID, 50_000L, BuncheolMemberAccessType.CLOSED)));
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

      then(buncheolMemberDomainService).should(never()).createBuncheolMembers(any(), anyList());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 운영진_LEGACY_개최_요청도_닫힌_자리는_거부한다() {
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
      HoldBuncheolRequest request =
          holdRequestWithFlow(
              List.of(new BuncheolMemberRequest(MEMBER_ID, 0L, BuncheolMemberAccessType.CLOSED)),
              FlowType.LEGACY);
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

      then(buncheolMemberDomainService).should(never()).createBuncheolMembers(any(), anyList());
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
      assertThat(event.initialUpload()).isTrue();
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

    // 성사 판정은 참여 행 수를 센다 — 최소 인원이 자리 수를 넘으면 성사될 수 없는 분철이 열린다.
    @Test
    void 최소_인원이_멤버_수를_넘으면_BCH_123_예외가_발생하고_분철을_만들지_않는다() {
      HoldBuncheolRequest request = holdRequestWithMinHeadcount(threeMembers(), 5);
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS))
          .given(buncheolDomainService)
          .validateMinHeadcountWithinSlots(5, 3);

      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
      then(buncheolMemberDomainService).should(never()).createBuncheolMembers(any(), anyList());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 최소_인원이_멤버_수와_같으면_자리_수로_검사를_통과해_개최된다() {
      HoldBuncheolRequest request = holdRequestWithMinHeadcount(threeMembers(), 3);
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);

      Long createdId = buncheolService.holdBuncheol(HOST_ID, request, images);

      assertThat(createdId).isEqualTo(BUNCHEOL_ID);
      then(buncheolDomainService).should().validateMinHeadcountWithinSlots(3, 3);
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(BUNCHEOL_ID), buncheolMemberParamsCaptor.capture());
      assertThat(buncheolMemberParamsCaptor.getValue()).hasSize(3);
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
  @DisplayName("운영진 분철 개최 테스트")
  class HoldStaffBuncheolTest {

    private final Instant deadline = NOW.plus(7, ChronoUnit.DAYS);
    private final List<ImageFile> images =
        List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

    private HoldStaffBuncheolRequest staffRequest(final Instant opensAt) {
      return new HoldStaffBuncheolRequest(
          GROUP_ID,
          "몬엑 분철",
          null,
          "공식 스토어",
          deadline,
          opensAt,
          1,
          3000,
          null,
          null,
          0,
          List.of(
              new BuncheolMemberRequest(MEMBER_ID, 15_000L),
              new BuncheolMemberRequest(MEMBER_ID + 1, 0L, BuncheolMemberAccessType.CODE_ONLY)));
    }

    private void givenCreatedBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
    }

    @Test
    void 운영진이_아니면_USR_031_이고_분철을_만들지_않는다() {
      given(userDomainService.canHost(HOST_ID)).willReturn(false);

      assertThatThrownBy(
              () ->
                  buncheolService.holdStaffBuncheol(
                      HOST_ID, staffRequest(NOW.plus(1, ChronoUnit.DAYS)), images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_CANNOT_HOST);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
      then(staffBuncheolSettingDomainService).shouldHaveNoInteractions();
      // 회원 개최의 C2C 자격 게이트를 타지 않는다.
      then(userDomainService).should(never()).requireC2cHostQualification(any());
    }

    @Test
    void 오픈_시각이_없으면_운영진_분철만_만들고_설정은_저장하지_않는다() {
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      givenCreatedBuncheol();

      Long createdId = buncheolService.holdStaffBuncheol(HOST_ID, staffRequest(null), images);

      assertThat(createdId).isEqualTo(BUNCHEOL_ID);
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(HOST_ID), buncheolParamsCaptor.capture());
      assertThat(buncheolParamsCaptor.getValue().flowType()).isEqualTo(FlowType.LEGACY);
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(BUNCHEOL_ID), buncheolMemberParamsCaptor.capture());
      assertThat(buncheolMemberParamsCaptor.getValue()).hasSize(2);
      then(staffBuncheolSettingDomainService).shouldHaveNoInteractions();
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
    }

    @Test
    void 오픈_시각이_있으면_만든_분철_id_로_설정을_저장한다() {
      Instant opensAt = NOW.plus(1, ChronoUnit.DAYS);
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      givenCreatedBuncheol();

      buncheolService.holdStaffBuncheol(HOST_ID, staffRequest(opensAt), images);

      InOrder order = inOrder(staffBuncheolSettingDomainService, buncheolDomainService);
      order.verify(staffBuncheolSettingDomainService).validateOpensAt(opensAt, deadline, NOW);
      order.verify(buncheolDomainService).createBuncheol(eq(HOST_ID), any());
      order.verify(staffBuncheolSettingDomainService).create(BUNCHEOL_ID, opensAt, deadline, NOW);
    }

    @Test
    void 오픈_시각이_잘못되면_BCH_129_이고_분철을_만들지_않는다() {
      Instant opensAt = deadline.plusSeconds(1);
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_OPENS_AT_INVALID))
          .given(staffBuncheolSettingDomainService)
          .validateOpensAt(opensAt, deadline, NOW);

      assertThatThrownBy(
              () -> buncheolService.holdStaffBuncheol(HOST_ID, staffRequest(opensAt), images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OPENS_AT_INVALID);

      then(buncheolDomainService).should(never()).createBuncheol(any(), any());
      then(buncheolMemberDomainService).should(never()).createBuncheolMembers(any(), anyList());
      then(staffBuncheolSettingDomainService).should(never()).create(any(), any(), any(), any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 회원_개최와_같은_자리_검증을_탄다() {
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
      HoldStaffBuncheolRequest request =
          new HoldStaffBuncheolRequest(
              GROUP_ID,
              "몬엑 분철",
              null,
              "공식 스토어",
              deadline,
              null,
              1,
              3000,
              null,
              null,
              0,
              List.of(
                  new BuncheolMemberRequest(
                      MEMBER_ID, 15_000L, BuncheolMemberAccessType.CODE_ONLY)));

      assertThatThrownBy(() -> buncheolService.holdStaffBuncheol(HOST_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_MEMBER_NOT_FREE);

      then(buncheolMemberDomainService).should(never()).createBuncheolMembers(any(), anyList());
    }

    @Test
    void 요청에는_개최_방식_칸이_없고_언제나_운영진_분철이다() {
      assertThat(staffRequest(null).toParams().flowType()).isEqualTo(FlowType.LEGACY);
      assertThat(HoldStaffBuncheolRequest.class.getRecordComponents())
          .extracting(RecordComponent::getName)
          .doesNotContain("flowType");
    }
  }

  @Nested
  @DisplayName("개최 자격 사전 조회 테스트 (docs/53 Q-07)")
  class GetHostingEligibilityTest {

    @Test
    void 자격을_모두_갖추면_적격이다() {
      // given
      given(userDomainService.evaluateC2cHostQualification(HOST_ID))
          .willReturn(C2cHostQualification.QUALIFIED);
      given(buncheolDomainService.isActiveHostedLimitExceeded(HOST_ID)).willReturn(false);
      given(userDomainService.hasBankAccount(HOST_ID)).willReturn(true);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.eligible()).isTrue();
      assertThat(response.reason()).isNull();
    }

    /**
     * 사유가 서로 뒤바뀌면 FE 안내가 정반대가 된다 — AGE_UNVERIFIED 는 "카카오 재동의로 열림", NOT_ADULT 는 "회복 불가"다. exhaustive
     * switch 는 누락만 잡고 뒤바뀜은 못 잡아 매핑을 따로 잠근다.
     */
    @ParameterizedTest
    @CsvSource({
      "PHONE_REQUIRED, PHONE_REQUIRED",
      "AGE_UNVERIFIED, AGE_UNVERIFIED",
      "NOT_ADULT, NOT_ADULT"
    })
    void 자격_판정이_같은_이름의_사유로_매핑된다(
        C2cHostQualification qualification, HostingEligibilityResponse.Reason expected) {
      // given
      given(userDomainService.evaluateC2cHostQualification(HOST_ID)).willReturn(qualification);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.eligible()).isFalse();
      assertThat(response.reason()).isEqualTo(expected);
    }

    @Test
    void 미성년이면_NOT_ADULT_사유로_부적격이다() {
      // given
      given(userDomainService.evaluateC2cHostQualification(HOST_ID))
          .willReturn(C2cHostQualification.NOT_ADULT);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.eligible()).isFalse();
      assertThat(response.reason()).isEqualTo(HostingEligibilityResponse.Reason.NOT_ADULT);
      // 자격 미달이면 상한은 볼 필요가 없다 — 제출 게이트의 검사 순서와 같다.
      then(buncheolDomainService).should(never()).isActiveHostedLimitExceeded(any());
    }

    @Test
    void 자격은_되지만_활성_개최_상한을_넘으면_LIMIT_EXCEEDED_사유로_부적격이다() {
      // given
      given(userDomainService.evaluateC2cHostQualification(HOST_ID))
          .willReturn(C2cHostQualification.QUALIFIED);
      given(buncheolDomainService.isActiveHostedLimitExceeded(HOST_ID)).willReturn(true);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.eligible()).isFalse();
      assertThat(response.reason()).isEqualTo(HostingEligibilityResponse.Reason.LIMIT_EXCEEDED);
    }

    @Test
    void 정산_계좌가_없으면_BANK_ACCOUNT_REQUIRED_사유로_부적격이다() {
      // given — 개최 요청이 자격·상한 뒤에 요구하는 검사(USR-025)라, 빠지면 폼을 다 채운 뒤에야 막힌다.
      given(userDomainService.evaluateC2cHostQualification(HOST_ID))
          .willReturn(C2cHostQualification.QUALIFIED);
      given(buncheolDomainService.isActiveHostedLimitExceeded(HOST_ID)).willReturn(false);
      given(userDomainService.hasBankAccount(HOST_ID)).willReturn(false);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.eligible()).isFalse();
      assertThat(response.reason())
          .isEqualTo(HostingEligibilityResponse.Reason.BANK_ACCOUNT_REQUIRED);
    }

    @Test
    void 운영진은_자격_게이트와_상한을_보지_않지만_정산_계좌는_본다() {
      // given — 운영진은 기본 LEGACY 라 C2C 자격 게이트·상한이 적용되지 않는다. 정산 계좌는 LEGACY·C2C 공통 요구다.
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      given(userDomainService.hasBankAccount(HOST_ID)).willReturn(true);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.eligible()).isTrue();
      then(userDomainService).should(never()).evaluateC2cHostQualification(any());
      then(buncheolDomainService).should(never()).isActiveHostedLimitExceeded(any());
    }

    @Test
    void 운영진도_정산_계좌가_없으면_부적격이다() {
      // given
      given(userDomainService.canHost(HOST_ID)).willReturn(true);
      given(userDomainService.hasBankAccount(HOST_ID)).willReturn(false);

      // when
      HostingEligibilityResponse response = buncheolService.getHostingEligibility(HOST_ID);

      // then
      assertThat(response.reason())
          .isEqualTo(HostingEligibilityResponse.Reason.BANK_ACCOUNT_REQUIRED);
    }
  }

  @Nested
  @DisplayName("오픈채팅 링크 전용 수정 테스트")
  class UpdateOpenChatUrlTest {

    @Test
    void 소유자와_수정_가능_구간을_확인한_뒤_링크를_교체한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.updateOpenChatUrl(
          HOST_ID, BUNCHEOL_ID, "https://open.kakao.com/o/newLink");

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheol).should().validateOpenChatUrlEditable();
      then(buncheolDomainService)
          .should()
          .replaceBuncheolOpenChatUrl(buncheol, "https://open.kakao.com/o/newLink");
    }

    // 전체 수정은 모집중·마감 전으로 묶여 있다 — 링크 경로가 그 가드를 타면 입금 구간에서 못 고친다.
    @Test
    void 전체_수정_가드는_타지_않는다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.updateOpenChatUrl(
          HOST_ID, BUNCHEOL_ID, "https://open.kakao.com/o/newLink");

      // then
      then(buncheol).should(never()).validateRecruiting(any(Instant.class));
    }

    // 제거는 빈 문자열로만 표현한다 (DTO @NotNull) — 서비스는 값을 해석하지 않고 그대로 넘긴다.
    @Test
    void 빈_문자열을_그대로_넘겨_도메인이_제거로_처리하게_한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.updateOpenChatUrl(HOST_ID, BUNCHEOL_ID, "");

      // then
      then(buncheolDomainService).should().replaceBuncheolOpenChatUrl(buncheol, "");
    }

    @Test
    void 수정_불가_구간이면_링크를_건드리지_않는다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_OPEN_CHAT_URL_NOT_EDITABLE))
          .given(buncheol)
          .validateOpenChatUrlEditable();

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolService.updateOpenChatUrl(
                      HOST_ID, BUNCHEOL_ID, "https://open.kakao.com/o/newLink"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OPEN_CHAT_URL_NOT_EDITABLE);
      then(buncheolDomainService).should(never()).replaceBuncheolOpenChatUrl(any(), any());
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

      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
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

      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
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
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);

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
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);

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
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images);

      // then
      then(buncheolImageDomainService).should().clearThumbnail(BUNCHEOL_ID);
      then(buncheolImageDomainService).should(never()).changeThumbnail(anyLong(), anyLong());
      then(eventPublisher).should().publishEvent(imageUploadEventCaptor.capture());
      BuncheolImageUploadEvent event = imageUploadEventCaptor.getValue();
      assertThat(event.buncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(event.thumbnailIndex()).isZero();
      assertThat(event.initialUpload()).isFalse();
    }

    @Test
    void 대표사진을_지정하지_않으면_예외가_발생하고_이미지_변경이_진행되지_않는다() {
      // given — 대표사진 지정은 필수(둘 중 정확히 하나)다.
      BuncheolModifyRequest request =
          new BuncheolModifyRequest("수정 제목", "수정 설명", List.of(1L), null, null, null);
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
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

      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
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
      given(buncheolDomainService.getBuncheolForUpdate(buncheolId))
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

      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
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

      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
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
  @DisplayName("분철 수정 — 거래 조건 테스트")
  class ModifyTermsTest {

    // slotEditBuncheol 의 지금 조건: 마감 NOW+1일 · 최소 3 · GS25 3,000(CU 미지원) · 구매처 "공식 스토어".
    private static final Instant CURRENT_DEADLINE = NOW.plus(1, ChronoUnit.DAYS);
    private static final Instant NEW_DEADLINE = NOW.plus(3, ChronoUnit.DAYS);

    private BuncheolModifyRequest termsRequest(
        final Instant deadline,
        final Integer minHeadcount,
        final Integer gs25ShippingFee,
        final Integer cuShippingFee,
        final String purchaseSite) {
      return new BuncheolModifyRequest(
          "수정 제목",
          "수정 설명",
          List.of(1L),
          1L,
          null,
          null,
          deadline,
          minHeadcount,
          gs25ShippingFee,
          cuShippingFee,
          purchaseSite);
    }

    private void givenLocked(final FlowType flowType) {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(flowType, BuncheolStatus.RECRUITING));
    }

    private void givenActiveParticipations(final int count) {
      List<Participation> active =
          IntStream.range(0, count)
              .mapToObj(i -> mock(Participation.class))
              .toList();
      given(participationDomainService.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(active);
    }

    private void givenSlots(final int openCount, final int closedCount) {
      List<BuncheolMember> slots = new ArrayList<>();
      for (long i = 0; i < openCount; i++) {
        slots.add(slot(100L + i, MEMBER_ID + i));
      }
      for (long i = 0; i < closedCount; i++) {
        slots.add(closedSlot(200L + i, MEMBER_ID + 50 + i));
      }
      given(buncheolMemberDomainService.findAllByBuncheolIdForShare(BUNCHEOL_ID)).willReturn(slots);
    }

    @Test
    void 조건_칸이_없으면_신청자를_묻지_않고_조건을_쓰지_않는다() {
      givenLocked(FlowType.C2C);

      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, modifyRequest(), List.of());

      then(participationDomainService).should(never()).findActiveByBuncheolId(anyLong());
      then(buncheolDomainService).should(never()).changeTermsIfNoApplicant(any(), any(), any());
      then(buncheolDomainService).should().updateBuncheolContent(any(), any(), any());
    }

    // 수정 화면이 조건 칸을 늘 함께 보내도 신청자가 있는 분철의 제목·사진 수정이 막히면 안 된다. LEGACY 도 같다.
    @ParameterizedTest
    @EnumSource(FlowType.class)
    void 지금_값을_그대로_보내면_조건을_건드리지_않고_제목은_바꾼다(final FlowType flowType) {
      givenLocked(flowType);

      buncheolService.modifyBuncheol(
          HOST_ID,
          BUNCHEOL_ID,
          termsRequest(CURRENT_DEADLINE, 3, 3000, null, "공식 스토어"),
          List.of());

      then(participationDomainService).should(never()).findActiveByBuncheolId(anyLong());
      then(buncheolDomainService).should(never()).changeTermsIfNoApplicant(any(), any(), any());
      then(buncheolDomainService).should().updateBuncheolContent(any(), eq("수정 제목"), any());
    }

    // 🔴 잠금 조회가 첫 분철 조회여야 조건 비교가 최신 값을 본다. 조건 UPDATE 는 제목 변경 뒤(flush 순서)·이미지 벌크 앞(실패 시 미실행).
    @Test
    void 신청자가_없으면_바뀐_조건을_제목_변경_뒤_조건부_UPDATE_로_쓴다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(0);
      BuncheolTerms expected =
          new BuncheolTerms(NEW_DEADLINE, 3, ShippingFeePolicy.of(2500, null), "공식 스토어");
      given(buncheolDomainService.changeTermsIfNoApplicant(BUNCHEOL_ID, expected, NOW))
          .willReturn(true);

      buncheolService.modifyBuncheol(
          HOST_ID, BUNCHEOL_ID, termsRequest(NEW_DEADLINE, null, 2500, null, null), List.of());

      then(buncheolDomainService).should(never()).getBuncheol(anyLong());
      InOrder order = inOrder(buncheolDomainService, buncheolImageDomainService);
      order.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      order.verify(buncheolDomainService).updateBuncheolContent(any(), any(), any());
      order.verify(buncheolDomainService).changeTermsIfNoApplicant(BUNCHEOL_ID, expected, NOW);
      order.verify(buncheolImageDomainService).deleteImagesExcluding(eq(BUNCHEOL_ID), anyList());
    }

    // 판정 뒤 들어온 신청은 조건부 UPDATE 가 막는다 — 예외로 제목 변경까지 롤백되고 이미지 벌크는 돌지 않아야 한다.
    @Test
    void 조건부_UPDATE_가_0행이면_409로_거부하고_이미지를_건드리지_않는다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(0);
      given(buncheolDomainService.changeTermsIfNoApplicant(eq(BUNCHEOL_ID), any(), eq(NOW)))
          .willReturn(false);
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));

      assertThatThrownBy(
              () ->
                  buncheolService.modifyBuncheol(
                      HOST_ID,
                      BUNCHEOL_ID,
                      termsRequest(NEW_DEADLINE, null, null, null, null),
                      images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT);

      then(buncheolImageDomainService).should(never()).deleteImagesExcluding(anyLong(), anyList());
      then(buncheolImageDomainService).should(never()).changeThumbnail(anyLong(), anyLong());
      then(buncheolImageDomainService).should(never()).clearThumbnail(anyLong());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    // 신청자가 있으면 값이 틀렸는지보다 「바꿀 수 없다」가 먼저다 — 값을 고쳐 와도 어차피 막힌다.
    @Test
    void 활성_신청이_있으면_값_검증보다_먼저_409로_거부한다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(1);
      Instant notOnTheHour = NEW_DEADLINE.plusSeconds(1800);

      assertThatThrownBy(
              () ->
                  buncheolService.modifyBuncheol(
                      HOST_ID,
                      BUNCHEOL_ID,
                      termsRequest(notOnTheHour, null, null, null, null),
                      List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT);

      then(buncheolDomainService).should(never()).updateBuncheolContent(any(), any(), any());
      then(buncheolDomainService).should(never()).changeTermsIfNoApplicant(any(), any(), any());
    }

    @Test
    void LEGACY_는_조건이_바뀌면_범용_플로우_가드로_거부한다() {
      givenLocked(FlowType.LEGACY);
      givenActiveParticipations(0);

      assertThatThrownBy(
              () ->
                  buncheolService.modifyBuncheol(
                      HOST_ID,
                      BUNCHEOL_ID,
                      termsRequest(NEW_DEADLINE, null, null, null, null),
                      List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);

      then(buncheolDomainService).should(never()).changeTermsIfNoApplicant(any(), any(), any());
    }

    @Test
    void 정각이_아닌_마감은_개최와_같은_코드로_거부하고_쓰지_않는다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(0);

      assertThatThrownBy(
              () ->
                  buncheolService.modifyBuncheol(
                      HOST_ID,
                      BUNCHEOL_ID,
                      termsRequest(NEW_DEADLINE.plusSeconds(1800), null, null, null, null),
                      List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_NOT_ON_THE_HOUR);

      then(buncheolDomainService).should(never()).updateBuncheolContent(any(), any(), any());
      then(buncheolDomainService).should(never()).changeTermsIfNoApplicant(any(), any(), any());
    }

    // 닫힌 자리는 신청을 받지 않는다 — 전체 4자리여도 열린 자리 3이면 최소 4는 채울 수 없다.
    @Test
    void 최소_인원이_열린_자리_수를_넘으면_거부한다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(0);
      givenSlots(3, 1);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS))
          .given(buncheolDomainService)
          .validateMinHeadcountWithinSlots(4, 3);

      assertThatThrownBy(
              () ->
                  buncheolService.modifyBuncheol(
                      HOST_ID, BUNCHEOL_ID, termsRequest(null, 4, null, null, null), List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS);

      then(buncheolDomainService).should(never()).changeTermsIfNoApplicant(any(), any(), any());
    }

    @Test
    void 최소_인원을_열린_자리_수로_낮추면_쓴다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(0);
      givenSlots(2, 1);
      given(buncheolDomainService.changeTermsIfNoApplicant(eq(BUNCHEOL_ID), any(), eq(NOW)))
          .willReturn(true);

      buncheolService.modifyBuncheol(
          HOST_ID, BUNCHEOL_ID, termsRequest(null, 2, null, null, null), List.of());

      then(buncheolDomainService).should().validateMinHeadcountWithinSlots(2, 2);
      then(buncheolDomainService)
          .should()
          .changeTermsIfNoApplicant(
              BUNCHEOL_ID,
              new BuncheolTerms(CURRENT_DEADLINE, 2, ShippingFeePolicy.of(3000, null), "공식 스토어"),
              NOW);
    }

    @Test
    void 최소_인원을_바꾸지_않으면_자리를_읽지_않는다() {
      givenLocked(FlowType.C2C);
      givenActiveParticipations(0);
      given(buncheolDomainService.changeTermsIfNoApplicant(eq(BUNCHEOL_ID), any(), eq(NOW)))
          .willReturn(true);

      buncheolService.modifyBuncheol(
          HOST_ID, BUNCHEOL_ID, termsRequest(null, null, null, 2000, "새 구매처"), List.of());

      then(buncheolMemberDomainService).should(never()).findAllByBuncheolIdForShare(anyLong());
      then(buncheolDomainService)
          .should()
          .changeTermsIfNoApplicant(
              BUNCHEOL_ID,
              new BuncheolTerms(CURRENT_DEADLINE, 3, ShippingFeePolicy.of(null, 2000), "새 구매처"),
              NOW);
    }

    @Test
    void 개최자가_아니면_신청자를_묻기_전에_거부한다() {
      givenLocked(FlowType.C2C);

      assertThatThrownBy(
              () ->
                  buncheolService.modifyBuncheol(
                      OTHER_USER_ID,
                      BUNCHEOL_ID,
                      termsRequest(NEW_DEADLINE, null, null, null, null),
                      List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(participationDomainService).should(never()).findActiveByBuncheolId(anyLong());
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
      // cascade 는 어느 묶음이 비었는지 개별로 알 수 없어 분철 범위로 일괄 판정한다.
      then(participationBundleDomainService).should().closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
      // 취소된 참여의 배송 스냅샷을 정리한다.
      then(deliveryDomainService).should().deleteByParticipationIds(List.of(50L));
      then(eventPublisher).should().publishEvent(any(BuncheolCancelledEvent.class));
    }

    // 참여마다 발행하면 다슬롯 참여자가 취소 알림을 슬롯 수만큼 받는다 — 리스너가 사람 단위로 묶으려면 한 번에 봐야 한다.
    @Test
    void 취소된_참여가_여러_건이어도_취소_이벤트는_분철_단위로_한_번_발행된다() {
      // given — cascade 재조회에는 ORDER BY 가 없다. 순서를 뒤섞어 발행 쪽 정렬을 확인한다.
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .willReturn(BuncheolStatus.RECRUITING);
      Participation later = mock(Participation.class);
      given(later.getId()).willReturn(52L);
      Participation earlier = mock(Participation.class);
      given(earlier.getId()).willReturn(50L);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(later, earlier));

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      ArgumentCaptor<BuncheolCancelledEvent> eventCaptor =
          ArgumentCaptor.forClass(BuncheolCancelledEvent.class);
      then(eventPublisher).should().publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue())
          .isEqualTo(
              new BuncheolCancelledEvent(
                  BUNCHEOL_ID, List.of(50L, 52L), BuncheolCancelReason.HOST_CANCELLED));
    }

    @Test
    void cascade_로_취소된_참여가_없으면_취소_이벤트를_발행하지_않는다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .willReturn(BuncheolStatus.RECRUITING);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      then(participationDomainService).should().cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
      // any() 로 두면 publishEvent(ApplicationEvent) 오버로드로 묶여 이 이벤트의 발행을 보지 못한다.
      then(eventPublisher).should(never()).publishEvent(any(BuncheolCancelledEvent.class));
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

    // docs/56 H-13 — 돈이 이미 개최자 계좌에 있는 분철은 접을 수 없다. 이 가드를 지우면 취소가 통과해 빨개진다.
    @Test
    void 입금_수집중_분철에_입금확인된_참여가_있으면_취소를_막고_전용_코드로_안내한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.PAYMENT_COLLECTING);
      given(participationDomainService.countConfirmedByBuncheolId(BUNCHEOL_ID)).willReturn(1);

      // when & then
      assertThatThrownBy(() -> buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_CONFIRMED_PAYMENT_EXISTS);

      then(buncheolDomainService).should(never()).cancelBuncheol(anyLong(), any());
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void 입금_수집중이어도_입금확인된_참여가_없으면_취소된다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.PAYMENT_COLLECTING);
      given(participationDomainService.countConfirmedByBuncheolId(BUNCHEOL_ID)).willReturn(0);
      given(buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .willReturn(BuncheolStatus.PAYMENT_COLLECTING);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      then(buncheolDomainService).should().cancelBuncheol(BUNCHEOL_ID, NOW);
    }

    // 모집중 구간은 그대로 열어 둔다 — C2C 는 이 구간에 입금확인이 있을 수 없고, LEGACY 는 회사 계좌 수납이라
    // 운영진 환불 경로가 따로 있다. 여기까지 조이면 기존 LEGACY 취소가 막힌다.
    @Test
    void 모집중_분철은_입금확인된_참여가_있어도_취소된다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);
      given(buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .willReturn(BuncheolStatus.RECRUITING);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      then(buncheolDomainService).should().cancelBuncheol(BUNCHEOL_ID, NOW);
      then(participationDomainService).should(never()).countConfirmedByBuncheolId(anyLong());
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

    // BCH-084 는 성사 확정·진행 확정·제외·보냈어요 등이 공유하는 범용 가드라, 특정 액션 전용
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

  @Nested
  @DisplayName("C2C 성사 확정 — 묶음 기한 채우기 (P2-b)")
  class ConfirmRecruitmentBundleDueAtTest {

    // 🔴 신청 구간의 묶음은 기한 없이 열린다. 성사 확정에서 안 채우면 bundles.due_at 이 영구 NULL 이고,
    // 나중에 붙을 개최자 「제외」 기한 가드가 fail-open 된다 — 기한 안인데도 참여자를 뺄 수 있게 된다.
    @Test
    void 성사_확정은_참여와_같은_기한을_묶음에도_채운다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(BankAccount.of("국민", "98765432", "개최자"));
      given(userDomainService.getUser(HOST_ID)).willReturn(host);
      given(buncheolDomainService.startCollecting(eq(BUNCHEOL_ID), any(), any(), eq(NOW)))
          .willReturn(true);
      given(participationDomainService.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationDomainService.startPaymentCollecting(eq(BUNCHEOL_ID), any(), eq(NOW)))
          .willReturn(1);

      buncheolService.confirmRecruitment(HOST_ID, BUNCHEOL_ID);

      Instant expectedDueAt = NOW.plus(ParticipationService.C2C_PAYMENT_WINDOW);
      then(participationBundleDomainService)
          .should()
          .assignDueAtByBuncheolId(BUNCHEOL_ID, expectedDueAt, NOW);
    }

    // 신청자가 전부 취소돼 전이가 0건이면 확정 자체가 롤백된다 — 묶음 기한도 채우면 안 된다.
    @Test
    void 전이된_참여가_없으면_묶음_기한도_채우지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(BankAccount.of("국민", "98765432", "개최자"));
      given(userDomainService.getUser(HOST_ID)).willReturn(host);
      given(buncheolDomainService.startCollecting(eq(BUNCHEOL_ID), any(), any(), eq(NOW)))
          .willReturn(true);
      given(participationDomainService.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationDomainService.startPaymentCollecting(eq(BUNCHEOL_ID), any(), eq(NOW)))
          .willReturn(0);

      assertThatThrownBy(() -> buncheolService.confirmRecruitment(HOST_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class);

      then(participationBundleDomainService)
          .should(never())
          .assignDueAtByBuncheolId(anyLong(), any(), any());
    }
  }

  @Nested
  @DisplayName("C2C 개최자 자리 빼기 테스트")
  class RemoveMemberTest {

    private static final Long SLOT_ID = 7L;
    private static final Long OTHER_SLOT_ID = 8L;

    private void givenLocked(final Buncheol buncheol) {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
    }

    private void givenSlots(final Long... slotIds) {
      givenSlots(List.of(), slotIds);
    }

    /** {@code closedSlotIds} 에 든 자리는 닫힌({@code CLOSED}) 자리로 만든다. */
    private void givenSlots(final List<Long> closedSlotIds, final Long... slotIds) {
      List<BuncheolMember> slots =
          Arrays.stream(slotIds)
              .map(
                  id ->
                      closedSlotIds.contains(id)
                          ? closedSlot(id, MEMBER_ID + id)
                          : slot(id, MEMBER_ID + id))
              .toList();
      given(buncheolMemberDomainService.findAllByBuncheolIdForUpdate(BUNCHEOL_ID))
          .willReturn(slots);
    }

    @Test
    void 개최자가_아니면_거부하고_아무것도_지우지_않는다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));

      assertThatThrownBy(() -> buncheolService.removeMember(OTHER_USER_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
    }

    @Test
    void LEGACY_분철이면_범용_플로우_가드로_거부한다() {
      givenLocked(slotEditBuncheol(FlowType.LEGACY, BuncheolStatus.RECRUITING));

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"CONFIRMED", "CANCELLED", "HOST_CANCELLED"})
    void 신청을_받지_않는_상태면_거부한다(final BuncheolStatus status) {
      givenLocked(slotEditBuncheol(FlowType.C2C, status));

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
    }

    // 마감이 지난 모집중(C2C 확정 유예 구간)은 신청을 받지 않는다 — 자리도 바꾸지 않는다.
    @Test
    void 마감이_지난_모집중이면_거부한다() {
      givenLocked(pastDeadlineC2cBuncheol());

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 그_분철의_자리가_아니면_없는_멤버로_거부한다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(OTHER_SLOT_ID, 9L);

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
    }

    @Test
    void 마지막_자리는_빼지_않는다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(SLOT_ID);
      given(participationDomainService.hasActiveParticipationOnSlot(SLOT_ID)).willReturn(false);

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REMOVE_LAST);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
    }

    // 신청자가 있는 분철에 「분철을 취소해 주세요」(BCH-120)를 권하면 안 된다 — 신청자 안내가 먼저다.
    @Test
    void 마지막_자리에_신청자가_있으면_마지막_자리보다_신청자_있음이_먼저다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(SLOT_ID);
      given(participationDomainService.hasActiveParticipationOnSlot(SLOT_ID)).willReturn(true);

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_APPLICANT);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
    }

    // 지우기·닫기 CAS 가 둘 다 실패하는 건 활성 참여가 있을 때뿐이다.
    @Test
    void 지우지도_닫지도_못하면_신청자_있음으로_거부하고_최소_인원은_그대로다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(SLOT_ID, OTHER_SLOT_ID);
      given(buncheolMemberDomainService.removeIfUnreferenced(SLOT_ID, BUNCHEOL_ID))
          .willReturn(false);
      given(buncheolMemberDomainService.closeIfUnoccupied(SLOT_ID, BUNCHEOL_ID)).willReturn(false);

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_APPLICANT);

      then(buncheolDomainService)
          .should(never())
          .lowerMinHeadcountToSlotCount(anyLong(), anyInt(), any());
    }

    // 🔴 취소 기록이 FK 로 붙은 빈 자리 — 지울 수는 없지만 신청은 계속 받으므로, 닫지 않으면 이중 판매가 그대로 난다.
    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "PAYMENT_COLLECTING"})
    void 기록만_남아_지우지_못한_자리는_닫고_열린_자리_수로_최소_인원을_맞춘다(final BuncheolStatus status) {
      givenLocked(slotEditBuncheol(FlowType.C2C, status));
      givenSlots(SLOT_ID, OTHER_SLOT_ID, 9L);
      given(buncheolMemberDomainService.removeIfUnreferenced(SLOT_ID, BUNCHEOL_ID))
          .willReturn(false);
      given(buncheolMemberDomainService.closeIfUnoccupied(SLOT_ID, BUNCHEOL_ID)).willReturn(true);

      buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID);

      then(buncheolDomainService).should().lowerMinHeadcountToSlotCount(BUNCHEOL_ID, 2, NOW);
    }

    // 화면이 낡았거나 두 번 누른 경우 — 이미 닫힌 자리는 아무것도 쓰지 않고 끝난다(204).
    @Test
    void 이미_닫힌_자리는_아무것도_바꾸지_않는다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(List.of(SLOT_ID), SLOT_ID, OTHER_SLOT_ID);

      buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
      then(buncheolMemberDomainService).should(never()).closeIfUnoccupied(any(), any());
      then(buncheolDomainService)
          .should(never())
          .lowerMinHeadcountToSlotCount(anyLong(), anyInt(), any());
    }

    // 닫힌 자리는 신청을 받지 않으므로 「남은 자리」가 아니다 — 열린 자리가 하나뿐이면 그게 마지막 자리다.
    @Test
    void 마지막_열린_자리_판정은_닫힌_자리를_세지_않는다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(List.of(OTHER_SLOT_ID), SLOT_ID, OTHER_SLOT_ID);
      given(participationDomainService.hasActiveParticipationOnSlot(SLOT_ID)).willReturn(false);

      assertThatThrownBy(() -> buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REMOVE_LAST);

      then(buncheolMemberDomainService).should(never()).removeIfUnreferenced(any(), any());
      then(buncheolMemberDomainService).should(never()).closeIfUnoccupied(any(), any());
    }

    @Test
    void 최소_인원은_닫힌_자리를_뺀_열린_자리_수로_맞춘다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(List.of(9L), SLOT_ID, OTHER_SLOT_ID, 9L);
      given(buncheolMemberDomainService.removeIfUnreferenced(SLOT_ID, BUNCHEOL_ID))
          .willReturn(true);

      buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID);

      then(buncheolDomainService).should().lowerMinHeadcountToSlotCount(BUNCHEOL_ID, 1, NOW);
    }

    // 남은 자리 수 = 빼기 전 자리 수 - 1. 모집중 여부는 UPDATE 조건이 가른다(입금 수집중이면 0행).
    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "PAYMENT_COLLECTING"})
    void 빈_자리는_지우고_남은_자리_수로_최소_인원_맞추기를_요청한다(final BuncheolStatus status) {
      givenLocked(slotEditBuncheol(FlowType.C2C, status));
      givenSlots(SLOT_ID, OTHER_SLOT_ID, 9L);
      given(buncheolMemberDomainService.removeIfUnreferenced(SLOT_ID, BUNCHEOL_ID))
          .willReturn(true);

      buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID);

      then(buncheolDomainService).should().lowerMinHeadcountToSlotCount(BUNCHEOL_ID, 2, NOW);
    }

    // 🔴 앞에서 getBuncheol 을 부르면 영속성 컨텍스트가 락 이전 값을 돌려준다 — 잠금 조회가 첫 분철 조회여야 하고,
    // 자리 잠금 → 조건부 삭제 순서여야 「마지막 자리」 판정이 낡지 않는다.
    @Test
    void 분철_잠금_조회가_첫_분철_조회이고_자리_잠금_뒤에_지운다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      givenSlots(SLOT_ID, OTHER_SLOT_ID);
      given(buncheolMemberDomainService.removeIfUnreferenced(SLOT_ID, BUNCHEOL_ID))
          .willReturn(true);

      buncheolService.removeMember(HOST_ID, BUNCHEOL_ID, SLOT_ID);

      then(buncheolDomainService).should(never()).getBuncheol(anyLong());
      InOrder order = inOrder(buncheolDomainService, buncheolMemberDomainService);
      order.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      order.verify(buncheolMemberDomainService).findAllByBuncheolIdForUpdate(BUNCHEOL_ID);
      order.verify(buncheolMemberDomainService).removeIfUnreferenced(SLOT_ID, BUNCHEOL_ID);
      order.verify(buncheolDomainService).lowerMinHeadcountToSlotCount(BUNCHEOL_ID, 1, NOW);
      // 지웠으면 닫지 않는다.
      then(buncheolMemberDomainService).should(never()).closeIfUnoccupied(any(), any());
    }
  }

  @Nested
  @DisplayName("C2C 개최자 자리 다시 넣기 테스트")
  class AddMemberTest {

    private static final Long NEW_SLOT_ID = 30L;

    private final BuncheolMemberAddRequest request =
        new BuncheolMemberAddRequest(MEMBER_ID, 15_000L);

    @Test
    void 개최자가_아니면_거부한다() {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));

      assertThatThrownBy(() -> buncheolService.addMember(OTHER_USER_ID, BUNCHEOL_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolMemberDomainService).should(never()).addOpenSlot(any(), any(), anyLong());
    }

    @Test
    void LEGACY_분철이면_범용_플로우_가드로_거부한다() {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.LEGACY, BuncheolStatus.RECRUITING));

      assertThatThrownBy(() -> buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    @Test
    void 신청을_받지_않는_상태면_거부한다() {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, BuncheolStatus.CONFIRMED));

      assertThatThrownBy(() -> buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolMemberDomainService).should(never()).addOpenSlot(any(), any(), anyLong());
    }

    @Test
    void 분철_그룹에_속하지_않는_멤버면_거부한다() {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      willThrow(new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP))
          .given(groupDomainService)
          .getGroupMembersByIdsInGroup(GROUP_ID, List.of(MEMBER_ID));

      assertThatThrownBy(() -> buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);

      then(buncheolMemberDomainService).should(never()).addOpenSlot(any(), any(), anyLong());
    }

    @Test
    void 이미_분철에_있는_멤버면_거부한다() {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      given(buncheolMemberDomainService.findInBuncheol(BUNCHEOL_ID, MEMBER_ID))
          .willReturn(Optional.of(slot(31L, MEMBER_ID)));

      assertThatThrownBy(() -> buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);

      then(buncheolMemberDomainService).should(never()).addOpenSlot(any(), any(), anyLong());
      // 열린 자리는 다시 열기 대상이 아니다 — 가격만 바꾸는 우회로가 되면 안 된다.
      then(buncheolMemberDomainService).should(never()).reopenClosedSlot(any(), any(), anyLong());
    }

    // 닫힌 자리는 기록이 FK 로 붙어 지울 수 없고 (분철, 멤버) 유니크라 새 행도 못 만든다 — 같은 자리를 다시 연다.
    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "PAYMENT_COLLECTING"})
    void 같은_멤버의_닫힌_자리가_있으면_같은_id_로_다시_연다(final BuncheolStatus status) {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, status));
      given(buncheolMemberDomainService.findInBuncheol(BUNCHEOL_ID, MEMBER_ID))
          .willReturn(Optional.of(closedSlot(31L, MEMBER_ID)));
      given(buncheolMemberDomainService.reopenClosedSlot(31L, BUNCHEOL_ID, 15_000L))
          .willReturn(true);

      Long slotId = buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request);

      assertThat(slotId).isEqualTo(31L);
      then(buncheolMemberDomainService).should(never()).addOpenSlot(any(), any(), anyLong());
      then(buncheolDomainService)
          .should(never())
          .lowerMinHeadcountToSlotCount(anyLong(), anyInt(), any());
    }

    // 다시 열기 CAS 는 닫힌 자리만 바꾼다 — 실패하면(그 사이 열림) 이미 있는 멤버다.
    @Test
    void 다시_열기_CAS_가_실패하면_이미_있는_멤버로_거부한다() {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      given(buncheolMemberDomainService.findInBuncheol(BUNCHEOL_ID, MEMBER_ID))
          .willReturn(Optional.of(closedSlot(31L, MEMBER_ID)));
      given(buncheolMemberDomainService.reopenClosedSlot(31L, BUNCHEOL_ID, 15_000L))
          .willReturn(false);

      assertThatThrownBy(() -> buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "PAYMENT_COLLECTING"})
    void 신청을_받는_동안에는_선착순_자리를_만들고_최소_인원은_올리지_않는다(final BuncheolStatus status) {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
          .willReturn(slotEditBuncheol(FlowType.C2C, status));
      given(buncheolMemberDomainService.addOpenSlot(BUNCHEOL_ID, MEMBER_ID, 15_000L))
          .willReturn(slot(NEW_SLOT_ID, MEMBER_ID));

      Long slotId = buncheolService.addMember(HOST_ID, BUNCHEOL_ID, request);

      assertThat(slotId).isEqualTo(NEW_SLOT_ID);
      then(groupDomainService).should().getGroupMembersByIdsInGroup(GROUP_ID, List.of(MEMBER_ID));
      then(buncheolDomainService)
          .should(never())
          .lowerMinHeadcountToSlotCount(anyLong(), anyInt(), any());
      then(buncheolDomainService).should(never()).getBuncheol(anyLong());
    }
  }

  @Nested
  @DisplayName("C2C 개최자 빈 자리 가격 바꾸기 테스트")
  class ChangeMemberPriceTest {

    private static final Long SLOT_ID = 7L;

    private void givenLocked(final Buncheol buncheol) {
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
    }

    @Test
    void 개최자가_아니면_거부하고_가격을_바꾸지_않는다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));

      assertThatThrownBy(
              () ->
                  buncheolService.changeMemberPrice(OTHER_USER_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolMemberDomainService)
          .should(never())
          .changePriceIfUnoccupied(any(), any(), anyLong());
    }

    @Test
    void LEGACY_분철이면_범용_플로우_가드로_거부한다() {
      givenLocked(slotEditBuncheol(FlowType.LEGACY, BuncheolStatus.RECRUITING));

      assertThatThrownBy(
              () -> buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"CONFIRMED", "CANCELLED", "HOST_CANCELLED"})
    void 신청을_받지_않는_상태면_거부한다(final BuncheolStatus status) {
      givenLocked(slotEditBuncheol(FlowType.C2C, status));

      assertThatThrownBy(
              () -> buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolMemberDomainService)
          .should(never())
          .changePriceIfUnoccupied(any(), any(), anyLong());
    }

    @Test
    void 마감이_지난_모집중이면_거부한다() {
      givenLocked(pastDeadlineC2cBuncheol());

      assertThatThrownBy(
              () -> buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 모집중_마감_전에는_빈_자리_가격을_바꾼다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      given(buncheolMemberDomainService.changePriceIfUnoccupied(SLOT_ID, BUNCHEOL_ID, 15_000L))
          .willReturn(true);

      buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L);

      then(buncheolMemberDomainService).should(never()).getBuncheolMember(any(), any());
    }

    // 🔴 빼기·넣기와 달리 추가 모집(입금 수집중)에는 열지 않는다 — 신청이 바로 입금 대기라 옛 가격을 띄운 화면이 틀린 입금액을
    // 안내할 수 있다. 이 검사를 지우면 가격이 바뀌어 빨개진다.
    @Test
    void 입금_수집중이면_거부하고_가격을_바꾸지_않는다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.PAYMENT_COLLECTING));

      assertThatThrownBy(
              () -> buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolMemberDomainService)
          .should(never())
          .changePriceIfUnoccupied(any(), any(), anyLong());
    }

    // 🔴 분철 잠금이 첫 분철 조회여야 신청(분철 잠금 → 자리 잠금 읽기)과 한 줄로 서서, 신청이 바뀐 가격을 본다.
    @Test
    void 분철_잠금이_첫_분철_조회이고_그_뒤에_가격을_쓴다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      given(buncheolMemberDomainService.changePriceIfUnoccupied(SLOT_ID, BUNCHEOL_ID, 15_000L))
          .willReturn(true);

      buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L);

      then(buncheolDomainService).should(never()).getBuncheol(anyLong());
      InOrder order = inOrder(buncheolDomainService, buncheolMemberDomainService);
      order.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      order.verify(buncheolMemberDomainService).changePriceIfUnoccupied(SLOT_ID, BUNCHEOL_ID, 15_000L);
    }

    @Test
    void 신청자가_있거나_닫힌_자리면_409로_거부한다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      given(buncheolMemberDomainService.changePriceIfUnoccupied(SLOT_ID, BUNCHEOL_ID, 15_000L))
          .willReturn(false);
      given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
          .willReturn(slot(SLOT_ID, MEMBER_ID));

      assertThatThrownBy(
              () -> buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_CHANGE_NOT_ALLOWED);
    }

    // 빼기와 같은 안내 — 이미 뺐거나 다른 분철의 자리면 없는 자리다.
    @Test
    void 그_분철의_자리가_아니면_없는_자리로_거부한다() {
      givenLocked(slotEditBuncheol(FlowType.C2C, BuncheolStatus.RECRUITING));
      given(buncheolMemberDomainService.changePriceIfUnoccupied(SLOT_ID, BUNCHEOL_ID, 15_000L))
          .willReturn(false);
      given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
          .willThrow(new BusinessException(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND));

      assertThatThrownBy(
              () -> buncheolService.changeMemberPrice(HOST_ID, BUNCHEOL_ID, SLOT_ID, 15_000L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);
    }
  }

  /** 자리 빼기·넣기용 실제 분철 — 개최자·플로우·상태 검사를 목이 아니라 도메인 코드로 태운다. */
  private Buncheol slotEditBuncheol(final FlowType flowType, final BuncheolStatus status) {
    Buncheol buncheol =
        Buncheol.create(
            HOST_ID,
            new BuncheolParams(
                GROUP_ID, "자리 분철", null, "공식 스토어", NOW.plus(1, ChronoUnit.DAYS), 3, 3000, null,
                flowType, null),
            NOW);
    setField(buncheol, "id", BUNCHEOL_ID);
    setField(buncheol, "status", status);
    return buncheol;
  }

  private Buncheol pastDeadlineC2cBuncheol() {
    Instant deadline = NOW.minus(1, ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            HOST_ID,
            new BuncheolParams(
                GROUP_ID, "자리 분철", null, "공식 스토어", deadline, 3, 3000, null, FlowType.C2C, null),
            deadline.minus(1, ChronoUnit.DAYS));
    setField(buncheol, "id", BUNCHEOL_ID);
    return buncheol;
  }

  private static BuncheolMember slot(final Long slotId, final Long groupMemberId) {
    BuncheolMember member = BuncheolMember.create(BUNCHEOL_ID, groupMemberId, 10_000L);
    setField(member, "id", slotId);
    return member;
  }

  private static BuncheolMember closedSlot(final Long slotId, final Long groupMemberId) {
    BuncheolMember member = slot(slotId, groupMemberId);
    setField(member, "accessType", BuncheolMemberAccessType.CLOSED);
    return member;
  }
}

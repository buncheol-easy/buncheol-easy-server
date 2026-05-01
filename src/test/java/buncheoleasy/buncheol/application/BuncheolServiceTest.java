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

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolModificationPolicy;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.MemberParticipationPresence;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

  @InjectMocks private BuncheolService buncheolService;

  @Mock private BuncheolDomainService buncheolDomainService;

  @Spy private BuncheolModificationPolicy buncheolModificationPolicy;

  @Mock private BuncheolImageDomainService buncheolImageDomainService;

  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;

  @Mock private ParticipationRepository participationRepository;

  @Mock private GroupDomainService groupDomainService;

  @Mock private UserDomainService userDomainService;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Captor private ArgumentCaptor<BuncheolParams> buncheolParamsCaptor;

  @Captor private ArgumentCaptor<List<BuncheolMemberParams>> buncheolMemberParamsCaptor;

  private static GroupMember groupMember(Long memberId) {
    return new GroupMember(
        memberId, GROUP_ID, MEMBER_NAME, MEMBER_IMAGE, LocalDateTime.now(), LocalDateTime.now());
  }

  private HoldBuncheolRequest holdRequest(List<BuncheolMemberRequest> members) {
    return new HoldBuncheolRequest(
        GROUP_ID,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 스토어",
        LocalDateTime.now().plusDays(7),
        7,
        3000,
        null,
        members);
  }

  private BuncheolModifyRequest modifyRequest(List<BuncheolMemberRequest> members) {
    return new BuncheolModifyRequest(
        GROUP_ID,
        "수정 분철 제목",
        "수정 설명",
        "수정 스토어",
        LocalDateTime.now().plusDays(10),
        10,
        3500,
        null,
        List.of(),
        members);
  }

  @Nested
  @DisplayName("분철 개최 테스트")
  class HoldBuncheolTest {

    @Test
    void 분철_개최에_성공하고_분철_및_분철_멤버가_저장된다() {
      // given
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 50_000L)));

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolDomainService.createBuncheol(eq(HOST_ID), any())).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.holdBuncheol(HOST_ID, request, List.of());

      // then
      then(groupDomainService).should().validateGroupExists(GROUP_ID);
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(HOST_ID), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(BUNCHEOL_ID), buncheolMemberParamsCaptor.capture());
      then(eventPublisher).should(never()).publishEvent(any());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isEqualTo(GROUP_ID);

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isEqualTo(MEMBER_ID);
      assertThat(memberParams.getFirst().bidMinPrice()).isEqualTo(50_000L);
    }

    @Test
    void 이미지가_있는_경우_이미지_업로드_이벤트가_발행된다() {
      // given
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 50_000L)));
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
    void 이미지_개수가_초과되면_예외가_발생한다() {
      // given
      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 50_000L)));
      List<ImageFile> images =
          List.of(
              new ImageFile("image1.jpg", "image/jpeg", new byte[] {1}),
              new ImageFile("image2.jpg", "image/jpeg", new byte[] {2}),
              new ImageFile("image3.jpg", "image/jpeg", new byte[] {3}),
              new ImageFile("image4.jpg", "image/jpeg", new byte[] {4}));

      willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED))
          .given(buncheolImageDomainService)
          .validateImageCount(4);

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
                  new BuncheolMemberRequest(null, MEMBER_ID, 50_000L),
                  new BuncheolMemberRequest(null, MEMBER_ID, 30_000L)));

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

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(null, 1L, 50_000L)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 호스트가_정산_계좌를_등록하지_않았으면_예외가_발생한다() {
      // given
      willThrow(new BusinessException(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED))
          .given(userDomainService)
          .requireBankAccountRegistered(HOST_ID);

      HoldBuncheolRequest request =
          holdRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 50_000L)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(HOST_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }
  }

  @Nested
  @DisplayName("분철 수정 테스트 - 참여자 없음")
  class ModifyBuncheolWithoutParticipantsTest {

    @Test
    void 분철_수정에_성공하고_분철_및_분철_멤버가_갱신된다() {
      // given
      BuncheolModifyRequest request =
          modifyRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 70_000L)));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(false);
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(groupDomainService).should().validateGroupExists(GROUP_ID);
      then(groupDomainService).should().getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList());
      then(buncheolDomainService)
          .should()
          .updateBuncheol(eq(buncheol), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(BUNCHEOL_ID), buncheolMemberParamsCaptor.capture());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isEqualTo(GROUP_ID);

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void 수정시_새_이미지가_있으면_업로드_이벤트를_발행한다() {
      // given
      BuncheolModifyRequest request =
          modifyRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(false);
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(MEMBER_ID)));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images);

      // then
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
    }

    @Test
    void 수정시_이미지_개수_초과면_예외가_발생한다() {
      // given
      BuncheolModifyRequest request =
          modifyRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
      List<ImageFile> images =
          List.of(
              new ImageFile("1.jpg", "image/jpeg", new byte[] {1}),
              new ImageFile("2.jpg", "image/jpeg", new byte[] {2}),
              new ImageFile("3.jpg", "image/jpeg", new byte[] {3}));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED))
          .given(buncheolImageDomainService)
          .validateImageCount(3);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    void 분철이_없으면_수정에_실패한다() {
      // given
      Long buncheolId = 999L;
      BuncheolModifyRequest request =
          modifyRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
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
      BuncheolModifyRequest request =
          modifyRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
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

      then(buncheolDomainService).should(never()).updateBuncheol(any(), any());
      then(buncheolMemberDomainService).should(never()).deleteAllByBuncheolId(anyLong());
      then(buncheolImageDomainService).should(never()).deleteImagesExcluding(anyLong(), anyList());
    }

    @Test
    void 모집중이_아닌_분철은_수정에_실패한다() {
      // given
      BuncheolModifyRequest request =
          modifyRequest(List.of(new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheol)
          .validateRecruiting();

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolDomainService).should(never()).updateBuncheol(any(), any());
    }

    @Test
    void 멤버_ID가_중복되면_예외가_발생한다() {
      // given
      BuncheolModifyRequest request =
          modifyRequest(
              List.of(
                  new BuncheolMemberRequest(null, MEMBER_ID, 70_000L),
                  new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(false);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }

    @Test
    void 그룹이_없으면_예외가_발생한다() {
      // given
      Long invalidGroupId = 999L;
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              invalidGroupId,
              "수정 분철 제목",
              null,
              "수정 스토어",
              LocalDateTime.now().plusDays(10),
              10,
              3500,
              null,
              List.of(),
              List.of(new BuncheolMemberRequest(null, MEMBER_ID, 60_000L)));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND))
          .given(groupDomainService)
          .validateGroupExists(invalidGroupId);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("분철 수정 테스트 - 참여자 존재")
  @MockitoSettings(strictness = Strictness.LENIENT)
  class ModifyBuncheolWithParticipantsTest {

    private Buncheol stubBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.getGroupId()).willReturn(GROUP_ID);
      given(buncheol.getStoreName()).willReturn("원래 스토어");
      given(buncheol.getShippingDeadlineDays()).willReturn(7);
      given(buncheol.getShippingFeePolicy()).willReturn(ShippingFeePolicy.of(3000, null));
      return buncheol;
    }

    private BuncheolModifyRequest preserveRequest(
        Integer gs25Fee, List<BuncheolMemberRequest> members) {
      return new BuncheolModifyRequest(
          GROUP_ID,
          "수정 제목",
          null,
          "원래 스토어",
          LocalDateTime.now().plusDays(7),
          7,
          gs25Fee,
          null,
          List.of(),
          members);
    }

    @Test
    void 잠긴_필드_변경_시_BCH080_에러가_발생한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      // storeName 변경
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              GROUP_ID,
              "수정 제목",
              null,
              "변경된 스토어",
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              List.of(),
              List.of(new BuncheolMemberRequest(1L, MEMBER_ID, 50_000L)));

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_FIELD_LOCKED);
    }

    @Test
    void 허용_필드만_변경하면_업데이트에_성공한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      BuncheolModifyRequest request =
          preserveRequest(3000, List.of(new BuncheolMemberRequest(1L, MEMBER_ID, 50_000L)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheolDomainService)
          .should()
          .updateBuncheol(eq(buncheol), buncheolParamsCaptor.capture());
      BuncheolParams params = buncheolParamsCaptor.getValue();
      assertThat(params.title()).isEqualTo("수정 제목");
    }

    @Test
    void 사용_중인_배송비_변경_시_BCH085_에러가_발생한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      // gs25ShippingFee 변경 (3000 → 4000)
      BuncheolModifyRequest request =
          preserveRequest(4000, List.of(new BuncheolMemberRequest(1L, MEMBER_ID, 50_000L)));

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of(ShippingMethod.GS25_HALF));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_SHIPPING_FEE_LOCKED);
    }

    @Test
    void 미사용_배송비_변경은_성공한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      // gs25 배송비 변경이지만 사용 중인 배송 방법에 GS25_HALF 없음
      BuncheolModifyRequest request =
          preserveRequest(4000, List.of(new BuncheolMemberRequest(1L, MEMBER_ID, 50_000L)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheolDomainService).should().updateBuncheol(eq(buncheol), any());
    }

    @Test
    void 활성_참여_멤버_삭제_시_BCH081_에러가_발생한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      // 요청에 멤버 1을 포함하지 않음 → 삭제 대상
      BuncheolModifyRequest request =
          preserveRequest(3000, List.of(new BuncheolMemberRequest(null, MEMBER_ID + 1, 30_000L)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(new MemberParticipationPresence(1L, true)));
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DELETE_LOCKED);
    }

    @Test
    void 참여_없는_멤버_삭제는_성공한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      Long newMemberId = MEMBER_ID + 1;
      // 요청에 멤버 1을 포함하지 않음 → 삭제 대상, 참여 없음
      BuncheolModifyRequest request =
          preserveRequest(3000, List.of(new BuncheolMemberRequest(null, newMemberId, 30_000L)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of()); // 참여 없음
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(existingMember));
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(newMemberId)));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheolMemberDomainService).should().deleteById(1L);
    }

    @Test
    void 활성_제시_있는_멤버의_bidMinPrice_내리기는_성공한다() {
      Buncheol buncheol = stubBuncheol();
      BuncheolModifyRequest request =
          preserveRequest(3000, List.of(new BuncheolMemberRequest(1L, MEMBER_ID, 20_000L)));

      BuncheolMember mockMember = mock(BuncheolMember.class);
      given(mockMember.getId()).willReturn(1L);
      given(mockMember.getBidMinPrice()).willReturn(25_000L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(new MemberParticipationPresence(1L, true)));
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(mockMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      then(mockMember).should().updateBidMinPrice(20_000L);
    }

    @Test
    void 활성_제시_있는_멤버의_bidMinPrice_올리기_시_BCH084_에러가_발생한다() {
      Buncheol buncheol = stubBuncheol();
      // bidMinPrice: 20_000 → 30_000 (올리기)
      BuncheolModifyRequest request =
          preserveRequest(3000, List.of(new BuncheolMemberRequest(1L, MEMBER_ID, 30_000L)));

      BuncheolMember mockMember = mock(BuncheolMember.class);
      given(mockMember.getId()).willReturn(1L);
      given(mockMember.getBidMinPrice()).willReturn(20_000L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(new MemberParticipationPresence(1L, true)));
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(mockMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_BID_MIN_INCREASE_LOCKED);
    }

    @Test
    void 수정_요청에_중복된_buncheolMemberId가_있으면_BCH087_에러가_발생한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      BuncheolModifyRequest request =
          preserveRequest(
              3000,
              List.of(
                  new BuncheolMemberRequest(1L, MEMBER_ID, 50_000L),
                  new BuncheolMemberRequest(1L, MEMBER_ID, 40_000L)));

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DUPLICATED);
    }

    @Test
    void 신규_멤버_추가에_성공한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      Long newMemberId = MEMBER_ID + 1;
      // 기존 멤버 유지 + 신규 멤버 추가
      BuncheolModifyRequest request =
          preserveRequest(
              3000,
              List.of(
                  new BuncheolMemberRequest(1L, MEMBER_ID, 50_000L),
                  new BuncheolMemberRequest(null, newMemberId, 30_000L)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of()); // 참여 없음
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(existingMember));
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(GROUP_ID), anyList()))
          .willReturn(List.of(groupMember(newMemberId)));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(BUNCHEOL_ID), buncheolMemberParamsCaptor.capture());
      List<BuncheolMemberParams> newParams = buncheolMemberParamsCaptor.getValue();
      assertThat(newParams).hasSize(1);
      assertThat(newParams.getFirst().memberId()).isEqualTo(newMemberId);
    }

    @Test
    void 존재하지_않는_buncheolMemberId_시_BCH086_에러가_발생한다() {
      // given
      Buncheol buncheol = stubBuncheol();
      // buncheolMemberId=999 → 존재하지 않는 멤버
      BuncheolModifyRequest request =
          preserveRequest(3000, List.of(new BuncheolMemberRequest(999L, MEMBER_ID, 50_000L)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(BUNCHEOL_ID)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(BUNCHEOL_ID))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_MEMBER_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("분철 취소 테스트")
  class CancelBuncheolTest {

    @Test
    void 분철_취소에_성공한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);

      // when
      buncheolService.cancelBuncheol(HOST_ID, BUNCHEOL_ID);

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheolDomainService).should().cancelBuncheol(buncheol, BuncheolStatus.RECRUITING);
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

      then(buncheolDomainService).should(never()).cancelBuncheol(any(), any());
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

      then(buncheolDomainService).should(never()).cancelBuncheol(any(), any());
    }
  }

  @Nested
  @DisplayName("분철 상태 진행 테스트")
  class AdvanceBuncheolStatusTest {

    @Test
    void 개최자가_상태를_정상_진행한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.CLOSED);
      willDoNothing().given(buncheol).validateOwner(HOST_ID);

      // when
      buncheolService.advanceBuncheolStatus(HOST_ID, BUNCHEOL_ID, BuncheolStatus.GOODS_ORDERED);

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheolDomainService)
          .should()
          .advanceBuncheolStatus(buncheol, BuncheolStatus.GOODS_ORDERED, BuncheolStatus.CLOSED);
    }

    @Test
    void 개최자가_아니면_예외가_발생한다() {
      // given
      Long hostId = 999L;
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(hostId);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolService.advanceBuncheolStatus(
                      hostId, BUNCHEOL_ID, BuncheolStatus.GOODS_ORDERED))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolDomainService).should(never()).advanceBuncheolStatus(any(), any(), any());
    }

    @Test
    void 전이_불가한_상태면_예외가_발생한다() {
      // given
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.CLOSED);
      willDoNothing().given(buncheol).validateOwner(HOST_ID);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED))
          .given(buncheolDomainService)
          .advanceBuncheolStatus(buncheol, BuncheolStatus.GOODS_ORDERED, BuncheolStatus.CLOSED);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolService.advanceBuncheolStatus(
                      HOST_ID, BUNCHEOL_ID, BuncheolStatus.GOODS_ORDERED))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED);
    }
  }
}

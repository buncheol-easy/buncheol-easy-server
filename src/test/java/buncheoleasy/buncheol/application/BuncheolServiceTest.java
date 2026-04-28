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
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BidOption;
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
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
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

  @InjectMocks private BuncheolService buncheolService;

  @Mock private BuncheolDomainService buncheolDomainService;

  @Spy private BuncheolModificationPolicy buncheolModificationPolicy;

  @Mock private BuncheolImageDomainService buncheolImageDomainService;

  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;

  @Mock private ParticipationRepository participationRepository;

  @Mock private GroupDomainService groupDomainService;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Captor private ArgumentCaptor<BuncheolParams> buncheolParamsCaptor;

  @Captor private ArgumentCaptor<List<BuncheolMemberParams>> buncheolMemberParamsCaptor;

  private HoldBuncheolRequest customGroupRequest() {
    return new HoldBuncheolRequest(
        null,
        "테스트 그룹",
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 앨범",
        "공식 스토어",
        50_000L,
        LocalDateTime.now().plusDays(7),
        7,
        3000,
        null,
        "국민은행",
        "123-456-789012",
        "홍길동",
        List.of(new BuncheolMemberRequest(null, null, "멤버A", 50_000L, false, null)));
  }

  private HoldBuncheolRequest officialGroupRequest(
      Long groupId, List<BuncheolMemberRequest> members) {
    return new HoldBuncheolRequest(
        groupId,
        null,
        "공식 그룹 분철 제목",
        null,
        "공식 앨범",
        "공식 스토어",
        50_000L,
        LocalDateTime.now().plusDays(7),
        7,
        3000,
        null,
        "신한은행",
        "123-456-789",
        "홍길동",
        members);
  }

  private BuncheolModifyRequest customGroupModifyRequest() {
    return new BuncheolModifyRequest(
        null,
        "수정 그룹",
        "수정 분철 제목",
        "수정 설명",
        "수정 굿즈",
        "수정 스토어",
        60_000L,
        LocalDateTime.now().plusDays(10),
        10,
        3500,
        null,
        "국민은행",
        "333-222-111",
        "수정홍길동",
        List.of(1L),
        List.of(new BuncheolMemberRequest(null, null, "수정멤버A", 60_000L, false, null)));
  }

  private BuncheolModifyRequest officialGroupModifyRequest(
      final Long groupId, final Long memberId) {
    return new BuncheolModifyRequest(
        groupId,
        null,
        "공식 수정 제목",
        "공식 수정 설명",
        "공식 수정 굿즈",
        "공식 수정 스토어",
        70_000L,
        LocalDateTime.now().plusDays(5),
        5,
        null,
        2500,
        "신한은행",
        "999-888-777",
        "공식수정",
        List.of(),
        List.of(new BuncheolMemberRequest(null, memberId, null, 70_000L, false, null)));
  }

  @Nested
  @DisplayName("커스텀 그룹 분철 개최 테스트")
  class HoldBuncheolWithCustomGroupTest {

    @Test
    void 커스텀_그룹으로_분철_개최에_성공한다() {
      // given
      Long hostId = 1L;
      HoldBuncheolRequest request = customGroupRequest();

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(10L);
      given(buncheolDomainService.createBuncheol(eq(hostId), any())).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.holdBuncheol(hostId, request, List.of());

      // then
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(hostId), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(10L), buncheolMemberParamsCaptor.capture());
      then(eventPublisher).should(never()).publishEvent(any());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isNull();
      assertThat(buncheolParams.groupName()).isEqualTo("테스트 그룹");
      assertThat(buncheolParams.title()).isEqualTo("테스트 분철 제목");

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isNull();
      assertThat(memberParams.getFirst().memberName()).isEqualTo("멤버A");
      assertThat(memberParams.getFirst().instantPrice()).isEqualTo(50_000L);
    }

    @Test
    void 이미지가_있는_경우_이미지_업로드_이벤트가_발행된다() {
      // given
      Long hostId = 1L;
      HoldBuncheolRequest request = customGroupRequest();
      List<ImageFile> images =
          List.of(new ImageFile("image1.jpg", "image/jpeg", new byte[] {1, 2, 3}));

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(10L);
      given(buncheolDomainService.createBuncheol(eq(hostId), any())).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

      // when
      buncheolService.holdBuncheol(hostId, request, images);

      // then
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
    }

    @Test
    void 이미지_개수가_초과되면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      HoldBuncheolRequest request = customGroupRequest();
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
      assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    void 커스텀_그룹에서_멤버명이_없으면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      HoldBuncheolRequest request =
          new HoldBuncheolRequest(
              null,
              "테스트 그룹",
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동",
              List.of(new BuncheolMemberRequest(null, null, null, 50_000L, false, null)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_NAME_REQUIRED);
    }
  }

  @Nested
  @DisplayName("공식 그룹 분철 개최 테스트")
  class HoldBuncheolWithOfficialGroupTest {

    @Test
    void 공식_그룹으로_분철_개최에_성공한다() {
      // given
      Long hostId = 1L;
      Long groupId = 100L;
      Long groupMemberId = 200L;

      Group group = new Group(groupId, "공식 그룹명", null, LocalDateTime.now(), LocalDateTime.now());
      given(groupDomainService.getGroup(groupId)).willReturn(group);

      GroupMember groupMember =
          new GroupMember(
              groupMemberId, groupId, "멤버A", null, LocalDateTime.now(), LocalDateTime.now());
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(groupId), anyList()))
          .willReturn(List.of(groupMember));

      HoldBuncheolRequest request =
          officialGroupRequest(
              groupId,
              List.of(new BuncheolMemberRequest(null, groupMemberId, null, 50_000L, false, null)));

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(10L);
      given(buncheolDomainService.createBuncheol(eq(hostId), any())).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.holdBuncheol(hostId, request, List.of());

      // then
      then(groupDomainService).should().getGroup(groupId);
      then(buncheolDomainService)
          .should()
          .createBuncheol(eq(hostId), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(10L), buncheolMemberParamsCaptor.capture());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isEqualTo(groupId);
      assertThat(buncheolParams.groupName()).isEqualTo("공식 그룹명");

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isEqualTo(groupMemberId);
      assertThat(memberParams.getFirst().memberName()).isEqualTo("멤버A");
      assertThat(memberParams.getFirst().instantPrice()).isEqualTo(50_000L);
    }

    @Test
    void 공식_그룹에서_멤버_ID가_없으면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long groupId = 100L;

      Group group = new Group(groupId, "공식 그룹명", null, LocalDateTime.now(), LocalDateTime.now());
      given(groupDomainService.getGroup(groupId)).willReturn(group);

      HoldBuncheolRequest request =
          officialGroupRequest(
              groupId, List.of(new BuncheolMemberRequest(null, null, "멤버A", 50_000L, false, null)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED);
    }

    @Test
    void 공식_그룹에서_멤버_ID가_중복되면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long groupId = 100L;
      Long groupMemberId = 200L;

      Group group = new Group(groupId, "공식 그룹명", null, LocalDateTime.now(), LocalDateTime.now());
      given(groupDomainService.getGroup(groupId)).willReturn(group);

      HoldBuncheolRequest request =
          officialGroupRequest(
              groupId,
              List.of(
                  new BuncheolMemberRequest(null, groupMemberId, null, 50_000L, false, null),
                  new BuncheolMemberRequest(null, groupMemberId, null, 30_000L, false, null)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }

    @Test
    void 존재하지_않는_그룹ID이면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long invalidGroupId = 999L;

      given(groupDomainService.getGroup(invalidGroupId))
          .willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND));

      HoldBuncheolRequest request =
          officialGroupRequest(
              invalidGroupId,
              List.of(new BuncheolMemberRequest(null, 1L, null, 50_000L, false, null)));

      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("분철 수정 테스트 - 참여자 없음")
  class ModifyBuncheolWithoutParticipantsTest {

    @Test
    void 커스텀_그룹_분철_수정에_성공한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      BuncheolModifyRequest request = customGroupModifyRequest();
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(false);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then
      then(buncheol).should().validateOwner(hostId);
      then(buncheolDomainService)
          .should()
          .updateBuncheol(eq(buncheol), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService).should().deleteAllByBuncheolId(buncheolId);
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(buncheolId), buncheolMemberParamsCaptor.capture());
      then(buncheolImageDomainService)
          .should()
          .deleteImagesExcluding(buncheolId, request.keepImageIds());
      then(eventPublisher).should(never()).publishEvent(any());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isNull();
      assertThat(buncheolParams.groupName()).isEqualTo("수정 그룹");
      assertThat(buncheolParams.title()).isEqualTo("수정 분철 제목");

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isNull();
      assertThat(memberParams.getFirst().memberName()).isEqualTo("수정멤버A");
      assertThat(memberParams.getFirst().instantPrice()).isEqualTo(60_000L);
    }

    @Test
    void 공식_그룹_분철_수정시_DB_그룹명과_멤버명이_적용된다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Long groupId = 100L;
      Long memberId = 200L;
      BuncheolModifyRequest request = officialGroupModifyRequest(groupId, memberId);
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(false);
      given(groupDomainService.getGroup(groupId))
          .willReturn(new Group(groupId, "공식 그룹명", null, LocalDateTime.now(), LocalDateTime.now()));
      given(groupDomainService.getGroupMembersByIdsInGroup(eq(groupId), anyList()))
          .willReturn(
              List.of(
                  new GroupMember(
                      memberId, groupId, "멤버A", null, LocalDateTime.now(), LocalDateTime.now())));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then
      then(groupDomainService).should().getGroup(groupId);
      then(groupDomainService).should().getGroupMembersByIdsInGroup(eq(groupId), anyList());
      then(buncheolDomainService)
          .should()
          .updateBuncheol(eq(buncheol), buncheolParamsCaptor.capture());
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(buncheolId), buncheolMemberParamsCaptor.capture());

      BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
      assertThat(buncheolParams.groupId()).isEqualTo(groupId);
      assertThat(buncheolParams.groupName()).isEqualTo("공식 그룹명");

      List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
      assertThat(memberParams).hasSize(1);
      assertThat(memberParams.getFirst().memberId()).isEqualTo(memberId);
      assertThat(memberParams.getFirst().memberName()).isEqualTo("멤버A");
    }

    @Test
    void 수정시_새_이미지가_있으면_업로드_이벤트를_발행한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      BuncheolModifyRequest request = customGroupModifyRequest();
      List<ImageFile> images = List.of(new ImageFile("new.jpg", "image/jpeg", new byte[] {1, 2}));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(false);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(2);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, images);

      // then
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
    }

    @Test
    void 수정시_이미지_개수_초과면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      BuncheolModifyRequest request = customGroupModifyRequest();
      List<ImageFile> images =
          List.of(
              new ImageFile("1.jpg", "image/jpeg", new byte[] {1}),
              new ImageFile("2.jpg", "image/jpeg", new byte[] {2}),
              new ImageFile("3.jpg", "image/jpeg", new byte[] {3}));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED))
          .given(buncheolImageDomainService)
          .validateImageCount(4);

      // when & then
      assertThatThrownBy(() -> buncheolService.modifyBuncheol(hostId, buncheolId, request, images))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    void 분철이_없으면_수정에_실패한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 999L;
      BuncheolModifyRequest request = customGroupModifyRequest();
      given(buncheolDomainService.getBuncheol(buncheolId))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }

    @Test
    void 소유자가_아니면_수정에_실패하고_업데이트를_진행하지_않는다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      BuncheolModifyRequest request = customGroupModifyRequest();
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(hostId);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
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
      Long hostId = 1L;
      Long buncheolId = 10L;
      BuncheolModifyRequest request = customGroupModifyRequest();
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheol)
          .validateRecruiting();

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(buncheolDomainService).should(never()).updateBuncheol(any(), any());
    }

    @Test
    void 커스텀_그룹_수정에서_멤버명이_없으면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "수정 그룹",
              "수정 분철 제목",
              null,
              "수정 굿즈",
              "수정 스토어",
              60_000L,
              LocalDateTime.now().plusDays(10),
              10,
              3500,
              null,
              "국민은행",
              "333-222-111",
              "수정홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(null, null, null, 60_000L, false, null)));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(false);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_NAME_REQUIRED);
    }

    @Test
    void 공식_그룹_수정에서_멤버_ID가_없으면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Long groupId = 100L;
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              groupId,
              null,
              "수정 제목",
              null,
              "수정 굿즈",
              "수정 스토어",
              70_000L,
              LocalDateTime.now().plusDays(10),
              10,
              3500,
              null,
              "신한은행",
              "123-456-789",
              "공식수정",
              List.of(),
              List.of(new BuncheolMemberRequest(null, null, null, 70_000L, false, null)));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(false);
      given(groupDomainService.getGroup(groupId))
          .willReturn(new Group(groupId, "공식 그룹명", null, LocalDateTime.now(), LocalDateTime.now()));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED);
    }

    @Test
    void 공식_그룹_수정에서_멤버_ID가_중복되면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Long groupId = 100L;
      Long memberId = 200L;
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              groupId,
              null,
              "수정 제목",
              null,
              "수정 굿즈",
              "수정 스토어",
              70_000L,
              LocalDateTime.now().plusDays(10),
              10,
              3500,
              null,
              "신한은행",
              "123-456-789",
              "공식수정",
              List.of(),
              List.of(
                  new BuncheolMemberRequest(null, memberId, null, 70_000L, false, null),
                  new BuncheolMemberRequest(null, memberId, null, 60_000L, false, null)));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(false);
      given(groupDomainService.getGroup(groupId))
          .willReturn(new Group(groupId, "공식 그룹명", null, LocalDateTime.now(), LocalDateTime.now()));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }

    @Test
    void 공식_그룹_수정에서_그룹이_없으면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Long groupId = 999L;
      BuncheolModifyRequest request = officialGroupModifyRequest(groupId, 200L);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(groupDomainService.getGroup(groupId))
          .willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
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
      given(buncheol.getId()).willReturn(10L);
      given(buncheol.getGroupId()).willReturn(null);
      given(buncheol.getGroupName()).willReturn("원래 그룹");
      given(buncheol.getGoodsName()).willReturn("원래 굿즈");
      given(buncheol.getStoreName()).willReturn("원래 스토어");
      given(buncheol.getOriginalPrice()).willReturn(50_000L);
      given(buncheol.getShippingDeadlineDays()).willReturn(7);
      given(buncheol.getShippingFeePolicy()).willReturn(ShippingFeePolicy.of(3000, null));
      return buncheol;
    }

    private BuncheolModifyRequest allowedFieldsOnlyRequest() {
      return new BuncheolModifyRequest(
          null,
          "원래 그룹",
          "수정 제목",
          "수정 설명",
          "원래 굿즈",
          "원래 스토어",
          50_000L,
          LocalDateTime.now().plusDays(14),
          7,
          3000,
          null,
          "수정은행",
          "수정계좌",
          "수정예금주",
          List.of(),
          List.of(new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null)));
    }

    @Test
    void 잠긴_필드_변경_시_BCH080_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // goodsName 변경
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "변경된 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null)));

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_FIELD_LOCKED);
    }

    @Test
    void 허용_필드만_변경하면_업데이트에_성공한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      BuncheolModifyRequest request = allowedFieldsOnlyRequest();

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then
      then(buncheolDomainService)
          .should()
          .updateBuncheol(eq(buncheol), buncheolParamsCaptor.capture());
      BuncheolParams params = buncheolParamsCaptor.getValue();
      assertThat(params.title()).isEqualTo("수정 제목");
      assertThat(params.settlementBank()).isEqualTo("수정은행");
    }

    @Test
    void 사용_중인_배송비_변경_시_BCH085_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // gs25ShippingFee 변경 (3000 → 4000)
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              4000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null)));

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of(ShippingMethod.GS25_HALF));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_SHIPPING_FEE_LOCKED);
    }

    @Test
    void 미사용_배송비_변경은_성공한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // gs25 배송비 변경이지만 사용 중인 배송 방법에 GS25_HALF 없음
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              4000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of()); // GS25 미사용
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then
      then(buncheolDomainService).should().updateBuncheol(eq(buncheol), any());
    }

    @Test
    void 활성_참여_멤버_삭제_시_BCH081_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // 요청에 멤버 1을 포함하지 않음 → 삭제 대상
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(null, null, "신규멤버", 30_000L, false, null)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of(new MemberParticipationPresence(1L, true, false)));
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DELETE_LOCKED);
    }

    @Test
    void 참여_없는_멤버_삭제는_성공한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // 요청에 멤버 1을 포함하지 않음 → 삭제 대상, 참여 없음
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(null, null, "신규멤버", 30_000L, false, null)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of()); // 참여 없음
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then
      then(buncheolMemberDomainService).should().deleteById(1L);
    }

    @Test
    void 즉시구매_참여_멤버_수정_시_BCH082_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // 멤버 1의 가격 변경
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 60_000L, false, null)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);
      given(existingMember.getInstantPrice()).willReturn(50_000L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of(new MemberParticipationPresence(1L, true, false)));
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_MEMBER_PRICE_LOCKED);
    }

    @Test
    void 제시만_있는_멤버_instantPrice_변경은_성공한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 60_000L, true, 20_000L)));

      BuncheolMember mockMember = mock(BuncheolMember.class);
      given(mockMember.getId()).willReturn(1L);
      given(mockMember.getInstantPrice()).willReturn(50_000L);
      given(mockMember.getBidOption()).willReturn(new BidOption(true, 25_000L));

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of(new MemberParticipationPresence(1L, false, true)));
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(mockMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then: 더티체크에 맡기므로 도메인 메서드 호출만 검증
      then(mockMember).should().updatePricing(60_000L, true, 20_000L);
    }

    @Test
    void 제시만_있는_멤버_bidMinPrice_올리기_시_BCH084_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // bidMinPrice: 20_000 → 30_000 (올리기)
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, true, 30_000L)));

      BuncheolMember mockMember = mock(BuncheolMember.class);
      given(mockMember.getId()).willReturn(1L);
      given(mockMember.getBidOption()).willReturn(new BidOption(true, 20_000L));

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of(new MemberParticipationPresence(1L, false, true)));
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(mockMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_BID_MIN_INCREASE_LOCKED);
    }

    @Test
    void 제시_참여_있는_멤버_bidAllowed_비활성화_시_BCH083_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // bidAllowed: true → false
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null)));

      BuncheolMember mockMember = mock(BuncheolMember.class);
      given(mockMember.getId()).willReturn(1L);
      given(mockMember.getBidOption()).willReturn(new BidOption(true, 20_000L));

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of(new MemberParticipationPresence(1L, false, true)));
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(mockMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_BID_DISABLE_LOCKED);
    }

    @Test
    void 수정_요청에_중복된_buncheolMemberId가_있으면_BCH087_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(
                  new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null),
                  new BuncheolMemberRequest(1L, null, "멤버A", 40_000L, false, null)));

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId)).willReturn(List.of());
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DUPLICATED);
    }

    @Test
    void 신규_멤버_추가에_성공한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // 기존 멤버 유지 + 신규 멤버 추가
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(
                  new BuncheolMemberRequest(1L, null, "멤버A", 50_000L, false, null),
                  new BuncheolMemberRequest(null, null, "신규멤버", 30_000L, false, null)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of()); // 참여 없음
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when
      buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of());

      // then
      then(buncheolMemberDomainService)
          .should()
          .createBuncheolMembers(eq(buncheolId), buncheolMemberParamsCaptor.capture());
      List<BuncheolMemberParams> newParams = buncheolMemberParamsCaptor.getValue();
      assertThat(newParams).hasSize(1);
      assertThat(newParams.getFirst().memberName()).isEqualTo("신규멤버");
    }

    @Test
    void 존재하지_않는_buncheolMemberId_시_BCH086_에러가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = stubBuncheol();
      // buncheolMemberId=999 → 존재하지 않는 멤버
      BuncheolModifyRequest request =
          new BuncheolModifyRequest(
              null,
              "원래 그룹",
              "수정 제목",
              null,
              "원래 굿즈",
              "원래 스토어",
              50_000L,
              LocalDateTime.now().plusDays(7),
              7,
              3000,
              null,
              "국민은행",
              "123",
              "홍길동",
              List.of(),
              List.of(new BuncheolMemberRequest(999L, null, "멤버A", 50_000L, false, null)));

      BuncheolMember existingMember = mock(BuncheolMember.class);
      given(existingMember.getId()).willReturn(1L);

      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(participationRepository.existsActiveByBuncheolId(buncheolId)).willReturn(true);
      given(participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId))
          .willReturn(Set.of());
      given(participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId))
          .willReturn(List.of());
      given(buncheolMemberDomainService.findAllByBuncheolId(buncheolId))
          .willReturn(List.of(existingMember));
      willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

      // when & then
      assertThatThrownBy(
              () -> buncheolService.modifyBuncheol(hostId, buncheolId, request, List.of()))
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
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(buncheol.getStatus())
          .willReturn(buncheoleasy.buncheol.domain.BuncheolStatus.RECRUITING);

      // when
      buncheolService.cancelBuncheol(hostId, buncheolId);

      // then
      then(buncheol).should().validateOwner(hostId);
      then(buncheolDomainService)
          .should()
          .cancelBuncheol(buncheol, buncheoleasy.buncheol.domain.BuncheolStatus.RECRUITING);
    }

    @Test
    void 소유자가_아니면_취소에_실패한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(hostId);

      // when & then
      assertThatThrownBy(() -> buncheolService.cancelBuncheol(hostId, buncheolId))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolDomainService).should(never()).cancelBuncheol(any(), any());
    }

    @Test
    void 분철이_없으면_취소에_실패한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 999L;
      given(buncheolDomainService.getBuncheol(buncheolId))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      // when & then
      assertThatThrownBy(() -> buncheolService.cancelBuncheol(hostId, buncheolId))
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
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(buncheoleasy.buncheol.domain.BuncheolStatus.CLOSED);
      willDoNothing().given(buncheol).validateOwner(hostId);

      // when
      buncheolService.advanceBuncheolStatus(
          hostId, buncheolId, buncheoleasy.buncheol.domain.BuncheolStatus.GOODS_ORDERED);

      // then
      then(buncheol).should().validateOwner(hostId);
      then(buncheolDomainService)
          .should()
          .advanceBuncheolStatus(
              buncheol,
              buncheoleasy.buncheol.domain.BuncheolStatus.GOODS_ORDERED,
              buncheoleasy.buncheol.domain.BuncheolStatus.CLOSED);
    }

    @Test
    void 개최자가_아니면_예외가_발생한다() {
      // given
      Long hostId = 999L;
      Long buncheolId = 10L;
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(hostId);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolService.advanceBuncheolStatus(
                      hostId,
                      buncheolId,
                      buncheoleasy.buncheol.domain.BuncheolStatus.GOODS_ORDERED))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(buncheolDomainService).should(never()).advanceBuncheolStatus(any(), any(), any());
    }

    @Test
    void 전이_불가한_상태면_예외가_발생한다() {
      // given
      Long hostId = 1L;
      Long buncheolId = 10L;
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(buncheolId)).willReturn(buncheol);
      given(buncheol.getStatus()).willReturn(buncheoleasy.buncheol.domain.BuncheolStatus.CLOSED);
      willDoNothing().given(buncheol).validateOwner(hostId);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED))
          .given(buncheolDomainService)
          .advanceBuncheolStatus(
              buncheol,
              buncheoleasy.buncheol.domain.BuncheolStatus.GOODS_ORDERED,
              buncheoleasy.buncheol.domain.BuncheolStatus.CLOSED);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolService.advanceBuncheolStatus(
                      hostId,
                      buncheolId,
                      buncheoleasy.buncheol.domain.BuncheolStatus.GOODS_ORDERED))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED);
    }
  }
}

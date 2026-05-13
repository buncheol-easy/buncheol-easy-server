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
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.UserDomainService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

  @InjectMocks private BuncheolService buncheolService;

  @Mock private BuncheolDomainService buncheolDomainService;

  @Mock private BuncheolImageDomainService buncheolImageDomainService;

  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;

  @Mock private GroupDomainService groupDomainService;

  @Mock private UserDomainService userDomainService;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Captor private ArgumentCaptor<BuncheolParams> buncheolParamsCaptor;

  @Captor private ArgumentCaptor<List<BuncheolMemberParams>> buncheolMemberParamsCaptor;

  @Captor private ArgumentCaptor<List<Long>> keepImageIdsCaptor;

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
        3000,
        null,
        members);
  }

  private BuncheolModifyRequest modifyRequest() {
    return new BuncheolModifyRequest("수정 분철 제목", "수정 설명", List.of());
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
          holdRequest(List.of(new BuncheolMemberRequest(MEMBER_ID, 50_000L)));

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
          new BuncheolModifyRequest("수정 제목", "수정 설명", List.of(1L, 2L));
      Buncheol buncheol = mock(Buncheol.class);

      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing().given(buncheolImageDomainService).validateImageCount(2);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, List.of());

      // then
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheol).should().validateRecruiting();
      then(buncheolDomainService)
          .should()
          .updateBuncheolContent(buncheol, "수정 제목", "수정 설명");
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
      willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

      // when
      buncheolService.modifyBuncheol(HOST_ID, BUNCHEOL_ID, request, images);

      // then
      then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
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
          .validateImageCount(6);

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
          .validateRecruiting();

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
}

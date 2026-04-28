package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationType;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolParticipationService 단위 테스트")
class BuncheolParticipationServiceTest {

  @InjectMocks private BuncheolParticipationService buncheolParticipationService;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;

  @Captor private ArgumentCaptor<Participation> participationCaptor;

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long BUNCHEOL_MEMBER_ID = 10L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;

  private Buncheol mockBuncheol() {
    Buncheol buncheol = mock(Buncheol.class);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
    return buncheol;
  }

  private BuncheolMember mockBuncheolMemberWithoutId() {
    BuncheolMember member = mock(BuncheolMember.class);
    given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
        .willReturn(member);
    return member;
  }

  private BuncheolMember mockBuncheolMember() {
    BuncheolMember member = mockBuncheolMemberWithoutId();
    given(member.getId()).willReturn(BUNCHEOL_MEMBER_ID);
    return member;
  }

  private ShippingAddress mockShippingAddress() {
    ShippingAddress address = mock(ShippingAddress.class);
    given(address.isOwnedBy(PARTICIPANT_ID)).willReturn(true);
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID)).willReturn(address);
    return address;
  }

  private ShippingAddress mockShippingAddressWithId() {
    ShippingAddress address = mockShippingAddress();
    given(address.getId()).willReturn(SHIPPING_ADDRESS_ID);
    return address;
  }

  private void mockNoActiveParticipation() {
    given(participationDomainService.findActiveParticipation(BUNCHEOL_MEMBER_ID, PARTICIPANT_ID))
        .willReturn(Optional.empty());
  }

  private void mockInstantSlotNotTaken() {
    given(participationDomainService.isInstantSlotTaken(BUNCHEOL_MEMBER_ID)).willReturn(false);
  }

  @Nested
  @DisplayName("즉시 구매 참여 생성 테스트")
  class CreateInstantParticipationTest {

    @Test
    void 즉시_구매_참여_생성에_성공한다() {
      // given
      Buncheol buncheol = mockBuncheol();
      BuncheolMember member = mockBuncheolMember();
      given(member.getInstantPrice()).willReturn(50_000L);
      mockShippingAddressWithId();
      mockNoActiveParticipation();
      mockInstantSlotNotTaken();

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      given(participationDomainService.createInstantParticipationIfRecruiting(any()))
          .willReturn(true);

      // when
      Participation result =
          buncheolParticipationService.createParticipation(BUNCHEOL_ID, PARTICIPANT_ID, request);

      // then
      assertThat(result.getType()).isEqualTo(ParticipationType.INSTANT);
      assertThat(result.getInstantPriceSnapshot()).isEqualTo(50_000L);

      then(participationDomainService)
          .should()
          .createInstantParticipationIfRecruiting(any(Participation.class));
    }

    @Test
    void 모집중이_아닌_분철이면_예외가_발생한다() {
      // given
      Buncheol buncheol = mockBuncheol();
      BuncheolMember member = mockBuncheolMember();
      given(member.getInstantPrice()).willReturn(50_000L);
      mockShippingAddressWithId();
      mockNoActiveParticipation();
      mockInstantSlotNotTaken();

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      given(participationDomainService.createInstantParticipationIfRecruiting(any()))
          .willReturn(false);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 즉시_구매_슬롯이_이미_점유되면_예외가_발생한다() {
      // given
      mockBuncheol();
      mockBuncheolMember();
      mockShippingAddress();
      mockNoActiveParticipation();
      given(participationDomainService.isInstantSlotTaken(BUNCHEOL_MEMBER_ID)).willReturn(true);

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_ALREADY_TAKEN);
    }
  }

  @Nested
  @DisplayName("제시 참여 생성 테스트")
  class CreateBidParticipationTest {

    @Test
    void 제시_참여_생성에_성공한다() {
      // given
      mockBuncheol();
      BuncheolMember member = mockBuncheolMember();
      mockShippingAddressWithId();
      mockNoActiveParticipation();
      mockInstantSlotNotTaken();

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.BID, 30_000L);

      given(participationDomainService.createBidParticipationIfNoActiveInstant(any()))
          .willReturn(true);

      // when
      Participation result =
          buncheolParticipationService.createParticipation(BUNCHEOL_ID, PARTICIPANT_ID, request);

      // then
      assertThat(result.getType()).isEqualTo(ParticipationType.BID);
      assertThat(result.getBidAmount()).isEqualTo(30_000L);

      then(member).should().validateBidAllowed();
      then(member).should().validateBidAmount(30_000L);
      then(participationDomainService)
          .should()
          .createBidParticipationIfNoActiveInstant(any(Participation.class));
    }

    @Test
    void 제시가_허용되지_않은_멤버이면_예외가_발생한다() {
      // given
      mockBuncheol();
      BuncheolMember member = mockBuncheolMember();
      mockShippingAddress();
      mockNoActiveParticipation();
      mockInstantSlotNotTaken();

      willThrow(
              new BusinessException(ErrorCode.PARTICIPATION_BID_NOT_ALLOWED))
          .given(member)
          .validateBidAllowed();

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.BID, 30_000L);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_BID_NOT_ALLOWED);

      then(participationDomainService)
          .should(never())
          .createBidParticipationIfNoActiveInstant(any());
    }
  }

  @Nested
  @DisplayName("공통 검증 테스트")
  class CommonValidationTest {

    @Test
    void 분철_모집중이_아니면_예외가_발생한다() {
      // given
      Buncheol buncheol = mockBuncheol();
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheol)
          .validateRecruiting();

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 호스트가_참여하면_예외가_발생한다() {
      // given
      Buncheol buncheol = mockBuncheol();
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(true);

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }

    @Test
    void 배송지_소유자가_아니면_예외가_발생한다() {
      // given
      mockBuncheol();
      mockBuncheolMemberWithoutId();
      ShippingAddress address = mock(ShippingAddress.class);
      given(address.isOwnedBy(PARTICIPANT_ID)).willReturn(false);
      given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
          .willReturn(address);

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }

    @Test
    void 이미_활성_참여가_있으면_예외가_발생한다() {
      // given
      mockBuncheol();
      mockBuncheolMember();
      mockShippingAddress();

      Participation existing = mock(Participation.class);
      given(participationDomainService.findActiveParticipation(BUNCHEOL_MEMBER_ID, PARTICIPANT_ID))
          .willReturn(Optional.of(existing));

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
    }

    @Test
    void 존재하지_않는_분철이면_예외가_발생한다() {
      // given
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      ParticipateRequest request =
          new ParticipateRequest(
              BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, ParticipationType.INSTANT, null);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, request))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }
  }
}

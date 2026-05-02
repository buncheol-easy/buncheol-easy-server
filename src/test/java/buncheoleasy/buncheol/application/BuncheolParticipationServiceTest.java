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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BuncheolParticipationService 단위 테스트")
class BuncheolParticipationServiceTest {

  @InjectMocks private BuncheolParticipationService buncheolParticipationService;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long BUNCHEOL_MEMBER_ID = 10L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;
  private static final long BID_AMOUNT = 30_000L;

  private Buncheol mockBuncheol() {
    Buncheol buncheol = mock(Buncheol.class);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
    return buncheol;
  }

  private BuncheolMember mockBuncheolMember() {
    BuncheolMember member = mock(BuncheolMember.class);
    given(member.getId()).willReturn(BUNCHEOL_MEMBER_ID);
    given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
        .willReturn(member);
    return member;
  }

  private ShippingAddress mockShippingAddress() {
    ShippingAddress address = mock(ShippingAddress.class);
    given(address.getId()).willReturn(SHIPPING_ADDRESS_ID);
    given(address.isOwnedBy(PARTICIPANT_ID)).willReturn(true);
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID)).willReturn(address);
    return address;
  }

  private void mockNoActiveParticipation() {
    given(participationDomainService.findActiveParticipation(BUNCHEOL_MEMBER_ID, PARTICIPANT_ID))
        .willReturn(Optional.empty());
  }

  private ParticipateRequest validRequest() {
    return new ParticipateRequest(BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
  }

  @Nested
  @DisplayName("참여 생성 테스트")
  class CreateParticipationTest {

    @Test
    void 참여_생성에_성공한다() {
      mockBuncheol();
      BuncheolMember member = mockBuncheolMember();
      mockShippingAddress();
      mockNoActiveParticipation();
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(true);

      Participation result =
          buncheolParticipationService.createParticipation(
              BUNCHEOL_ID, PARTICIPANT_ID, validRequest());

      assertThat(result.getBidAmount()).isEqualTo(BID_AMOUNT);
      then(member).should().validateBidAmount(BID_AMOUNT);
      then(participationDomainService)
          .should()
          .createParticipationIfRecruiting(any(Participation.class));
    }

    @Test
    void 모집중이_아닌_분철이면_예외가_발생한다() {
      mockBuncheol();
      mockBuncheolMember();
      mockShippingAddress();
      mockNoActiveParticipation();
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(false);

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 제시_금액이_최소_금액보다_작으면_예외가_발생한다() {
      mockBuncheol();
      BuncheolMember member = mockBuncheolMember();
      mockShippingAddress();
      mockNoActiveParticipation();

      willThrow(new BusinessException(ErrorCode.PARTICIPATION_BID_AMOUNT_INVALID))
          .given(member)
          .validateBidAmount(BID_AMOUNT);

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_BID_AMOUNT_INVALID);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }
  }

  @Nested
  @DisplayName("공통 검증 테스트")
  class CommonValidationTest {

    @Test
    void 분철_모집중이_아니면_예외가_발생한다() {
      Buncheol buncheol = mockBuncheol();
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheol)
          .validateRecruiting();

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 호스트가_참여하면_예외가_발생한다() {
      Buncheol buncheol = mockBuncheol();
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(true);

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }

    @Test
    void 배송지_소유자가_아니면_예외가_발생한다() {
      mockBuncheol();
      mockBuncheolMember();
      ShippingAddress address = mock(ShippingAddress.class);
      given(address.isOwnedBy(PARTICIPANT_ID)).willReturn(false);
      given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
          .willReturn(address);

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }

    @Test
    void 이미_활성_참여가_있으면_예외가_발생한다() {
      mockBuncheol();
      mockBuncheolMember();
      mockShippingAddress();

      Participation existing = mock(Participation.class);
      given(participationDomainService.findActiveParticipation(BUNCHEOL_MEMBER_ID, PARTICIPANT_ID))
          .willReturn(Optional.of(existing));

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
    }

    @Test
    void 존재하지_않는_분철이면_예외가_발생한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      assertThatThrownBy(
              () ->
                  buncheolParticipationService.createParticipation(
                      BUNCHEOL_ID, PARTICIPANT_ID, validRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }
  }
}

package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolImageResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberSaleStatus;
import buncheoleasy.buncheol.dto.response.MyParticipationItemResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolDetailQueryService 단위 테스트")
class BuncheolDetailQueryServiceTest {

  @InjectMocks private BuncheolDetailQueryService buncheolDetailQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private ParticipationRepository participationRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long GROUP_ID = 100L;
  private static final Long ME = 999L;
  private static final Long OTHER_USER = 888L;
  private static final Long HOST_ID = 777L;
  private static final Instant NOW = Instant.parse("2026-05-20T12:00:00Z");
  private static final Instant DEADLINE = Instant.parse("2026-06-01T12:00:00Z");
  private static final Instant PAYMENT_DUE_AT = Instant.parse("2026-05-20T10:30:00Z");

  // Instant.now(clock) 가 실제 시각을 돌려주도록 고정 Clock 을 @Spy 로 주입한다 (mock Clock 은 NPE).
  // 기본 DEADLINE 은 NOW 이후라, 따로 지정하지 않으면 모집중 분철은 "마감 전" 으로 판정된다.
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Nested
  @DisplayName("분철 단건 상세 조회")
  class GetDetailTest {

    @Test
    void 존재하지_않는_분철은_BUNCHEOL_NOT_FOUND() {
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }

    @Test
    void 분철은_있지만_그룹이_없으면_GROUP_NOT_FOUND() {
      Buncheol buncheol =
          buncheol(
              BUNCHEOL_ID,
              GROUP_ID,
              "제목",
              BuncheolStatus.RECRUITING,
              ShippingFeePolicy.of(3000, null));
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));
      given(groupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 비로그인_호출시_myParticipation_은_null() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, 4000));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(image(1L, "img-a.jpg"), image(2L, "img-b.jpg")));
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.myParticipation()).isNull();
      assertThat(response.hostedByMe()).isFalse();
      assertThat(response.images())
          .extracting(BuncheolImageResponse::url)
          .containsExactly("img-a.jpg", "img-b.jpg");
      assertThat(response.shippingOptions())
          .extracting("method", "fee")
          .containsExactly(
              tuple(ShippingMethod.GS25_HALF, 3000), tuple(ShippingMethod.CU_HALF, 4000));
    }

    @Test
    void 비로그인_호출이면_선점된_슬롯도_participatedByMe_는_false() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(active(501L, 101L, OTHER_USER, ParticipationStatus.AWAITING_PAYMENT)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.AWAITING_PAYMENT);
      assertThat(response.members().get(0).participatedByMe()).isFalse();
    }

    @Test
    void 호스트_본인이_조회하면_hostedByMe_가_true() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, HOST_ID);

      assertThat(response.hostedByMe()).isTrue();
    }

    @Test
    void 호스트가_아닌_로그인_유저가_조회하면_hostedByMe_가_false() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.hostedByMe()).isFalse();
    }

    @Test
    void 로그인_미참여_상태에서_myParticipation_은_빈_요약() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      // 다른 유저가 슬롯 101 을 점유 중 (활성 참여 1건).
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(active(501L, 101L, OTHER_USER, ParticipationStatus.AWAITING_PAYMENT)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.myParticipation()).isNotNull();
      assertThat(response.myParticipation().participatedMemberCount()).isZero();
      assertThat(response.myParticipation().participations()).isEmpty();
      // 입금 확인을 기다리는 활성 참여가 점유한 멤버 슬롯은 AWAITING_PAYMENT + 입금 기한으로 표시된다.
      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.AWAITING_PAYMENT);
      assertThat(response.members().get(0).paymentDueAt()).isEqualTo(PAYMENT_DUE_AT);
      // 다른 유저의 선점이므로 내 참여 아님.
      assertThat(response.members().get(0).participatedByMe()).isFalse();
    }

    @Test
    void 멤버별_가격과_판매_상태_그리고_내_참여_요약을_계산한다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  buncheolMember(101L, BUNCHEOL_ID, 1001L, 40_000L),
                  buncheolMember(102L, BUNCHEOL_ID, 1002L, 30_000L),
                  buncheolMember(103L, BUNCHEOL_ID, 1003L, 20_000L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L, 1003L)))
          .willReturn(
              List.of(
                  groupMember(1001L, "민지", "minji.png"),
                  groupMember(1002L, "해린", "haerin.png"),
                  groupMember(1003L, "혜인", "hyein.png")));
      // 슬롯 101: 내가 입금확인중 점유, 슬롯 102: 내가 확정 점유, 슬롯 103: 다른 유저 점유
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  active(601L, 101L, ME, ParticipationStatus.AWAITING_PAYMENT),
                  active(602L, 102L, ME, ParticipationStatus.CONFIRMED),
                  active(603L, 103L, OTHER_USER, ParticipationStatus.AWAITING_PAYMENT)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.confirmedCount()).isEqualTo(1);
      assertThat(response.members())
          .extracting(
              BuncheolMemberDetailResponse::buncheolMemberId,
              BuncheolMemberDetailResponse::memberName,
              BuncheolMemberDetailResponse::price,
              BuncheolMemberDetailResponse::saleStatus,
              BuncheolMemberDetailResponse::paymentDueAt,
              BuncheolMemberDetailResponse::participatedByMe)
          .containsExactly(
              tuple(
                  101L, "민지", 40_000L, BuncheolMemberSaleStatus.AWAITING_PAYMENT, PAYMENT_DUE_AT,
                  true),
              tuple(102L, "해린", 30_000L, BuncheolMemberSaleStatus.SOLD, null, true),
              tuple(
                  103L, "혜인", 20_000L, BuncheolMemberSaleStatus.AWAITING_PAYMENT, PAYMENT_DUE_AT,
                  false));

      assertThat(response.myParticipation().participatedMemberCount()).isEqualTo(2);
      assertThat(response.myParticipation().participations())
          .extracting(
              MyParticipationItemResponse::participationId,
              MyParticipationItemResponse::buncheolMemberId,
              MyParticipationItemResponse::status)
          .containsExactly(
              tuple(601L, 101L, ParticipationStatus.AWAITING_PAYMENT),
              tuple(602L, 102L, ParticipationStatus.CONFIRMED));
    }

    @Test
    void 활성_참여가_없는_멤버_슬롯은_AVAILABLE() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L, 40_000L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.AVAILABLE);
      assertThat(response.members().get(0).paymentDueAt()).isNull();
      assertThat(response.confirmedCount()).isZero();
    }

    // docs/53 Q-14 — 신청이 409(BCH-060) 로 막히는 분철의 공석이 AVAILABLE 로 내려가 신청 가능해 보이던 문제.
    @Test
    void 진행확정된_분철의_빈_슬롯은_CLOSED_로_내려간다() {
      stubEmptySlot(BuncheolStatus.CONFIRMED, FlowType.C2C);

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.CLOSED);
      assertThat(response.members().get(0).paymentDueAt()).isNull();
      assertThat(response.members().get(0).participatedByMe()).isFalse();
    }

    @Test
    void 취소된_분철의_빈_슬롯은_CLOSED_로_내려간다() {
      stubEmptySlot(BuncheolStatus.CANCELLED, FlowType.C2C);

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.CLOSED);
    }

    // docs/46 §4.7-E1 — C2C 는 성사 확정 후 입금 수집중 구간에도 빈 슬롯 추가 모집을 받는다.
    @Test
    void 입금_수집중인_C2C_분철의_빈_슬롯은_AVAILABLE_을_유지한다() {
      stubEmptySlot(BuncheolStatus.PAYMENT_COLLECTING, FlowType.C2C);

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.AVAILABLE);
    }

    // 마감 후에도 개최자 성사 확정 전까지 최대 48시간 RECRUITING 에 머무는데(C2C 확정 유예 — docs/46 §4.7-E3),
    // 이 구간의 신청은 Buncheol#validateRecruiting 에서 409(BCH-060) 로 막힌다.
    @Test
    void 마감_시각이_지난_모집중_분철의_빈_슬롯은_CLOSED_로_내려간다() {
      stubEmptySlot(BuncheolStatus.RECRUITING, FlowType.C2C, NOW.minusSeconds(1));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.CLOSED);
    }

    @Test
    void 마감_시각_직전의_모집중_분철의_빈_슬롯은_AVAILABLE_이다() {
      stubEmptySlot(BuncheolStatus.RECRUITING, FlowType.C2C, NOW.plusSeconds(1));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.AVAILABLE);
    }

    // PAYMENT_COLLECTING + LEGACY 는 프로덕션에 생길 수 없는 조합이다(진입 경로가 C2C 성사 확정뿐).
    // 추가 모집을 flowType 으로 가르는 방어 분기를 고정하는 테스트다.
    @Test
    void 입금_수집중이어도_LEGACY_분철의_빈_슬롯은_CLOSED_로_내려간다() {
      stubEmptySlot(BuncheolStatus.PAYMENT_COLLECTING, FlowType.LEGACY);

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).saleStatus())
          .isEqualTo(BuncheolMemberSaleStatus.CLOSED);
    }

    @Test
    void 진행확정된_분철이라도_점유된_슬롯의_판매_상태는_그대로다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED, ShippingFeePolicy.of(3000, null), FlowType.C2C);
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  buncheolMember(101L, BUNCHEOL_ID, 1001L, 40_000L),
                  buncheolMember(102L, BUNCHEOL_ID, 1002L, 30_000L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png"), groupMember(1002L, "해린", null)));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(active(601L, 101L, ME, ParticipationStatus.CONFIRMED)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members())
          .extracting(
              BuncheolMemberDetailResponse::buncheolMemberId,
              BuncheolMemberDetailResponse::saleStatus,
              BuncheolMemberDetailResponse::participatedByMe)
          .containsExactly(
              tuple(101L, BuncheolMemberSaleStatus.SOLD, true),
              tuple(102L, BuncheolMemberSaleStatus.CLOSED, false));
    }

    @Test
    void 멤버_슬롯이_없는_분철도_정상_응답한다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED, ShippingFeePolicy.of(null, 4000));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members()).isEmpty();
      assertThat(response.shippingOptions())
          .extracting("method", "fee")
          .containsExactly(tuple(ShippingMethod.CU_HALF, 4000));
    }

    @Test
    void CANCELLED_분철도_200_으로_status_와_minHeadcount_를_포함해_반환한다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.status()).isEqualTo(BuncheolStatus.CANCELLED);
      assertThat(response.minHeadcount()).isEqualTo(3);
    }

    @Test
    void 대표사진_플래그가_있으면_이미지는_등록순을_유지하고_플래그_이미지만_thumbnail이_true다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      // 두 번째 이미지가 대표사진 — 순서는 바뀌지 않고 thumbnail 플래그로만 식별된다.
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(image(1L, "img-a.jpg"), image(2L, "img-b.jpg", true)));
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.images())
          .extracting(
              BuncheolImageResponse::id, BuncheolImageResponse::url, BuncheolImageResponse::thumbnail)
          .containsExactly(tuple(1L, "img-a.jpg", false), tuple(2L, "img-b.jpg", true));
    }

    @Test
    void 대표사진_플래그가_없으면_첫_이미지가_thumbnail_true로_폴백된다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(image(1L, "img-a.jpg"), image(2L, "img-b.jpg")));
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.images())
          .extracting(
              BuncheolImageResponse::id, BuncheolImageResponse::url, BuncheolImageResponse::thumbnail)
          .containsExactly(tuple(1L, "img-a.jpg", true), tuple(2L, "img-b.jpg", false));
    }

    @Test
    void 이미지가_없으면_images는_빈_리스트다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.images()).isEmpty();
    }

    @Test
    void 개최자가_취소한_HOST_CANCELLED_분철은_BUNCHEOL_NOT_FOUND() {
      Buncheol hostCancelled =
          buncheol(
              BUNCHEOL_ID, GROUP_ID, "개최자 취소 분철",
              BuncheolStatus.HOST_CANCELLED, ShippingFeePolicy.of(3000, null));
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(hostCancelled));

      assertThatThrownBy(() -> buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }
  }

  // flow_type 은 NOT NULL 컬럼이라 프로덕션에 null 은 없다 — 기본값을 LEGACY 로 둔다.
  private void stubBasicBuncheol(final BuncheolStatus status, final ShippingFeePolicy policy) {
    stubBasicBuncheol(status, policy, FlowType.LEGACY);
  }

  private void stubBasicBuncheol(
      final BuncheolStatus status, final ShippingFeePolicy policy, final FlowType flowType) {
    stubBasicBuncheol(status, policy, flowType, DEADLINE);
  }

  private void stubBasicBuncheol(
      final BuncheolStatus status,
      final ShippingFeePolicy policy,
      final FlowType flowType,
      final Instant deadline) {
    Buncheol buncheol =
        buncheol(BUNCHEOL_ID, GROUP_ID, "뉴진스 1집 분철", status, policy, flowType, deadline);
    given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));
    given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group(GROUP_ID, "뉴진스")));
  }

  /** 활성 참여가 하나도 없는(= 전 슬롯 공석) 분철 스텁 — 공석 판매 상태 판정 전용. */
  private void stubEmptySlot(final BuncheolStatus status, final FlowType flowType) {
    stubEmptySlot(status, flowType, DEADLINE);
  }

  private void stubEmptySlot(
      final BuncheolStatus status, final FlowType flowType, final Instant deadline) {
    stubBasicBuncheol(status, ShippingFeePolicy.of(3000, null), flowType, deadline);
    given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID)).willReturn(List.of());
    given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
        .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L, 40_000L)));
    given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
        .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
    given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
  }

  private Buncheol buncheol(
      Long id,
      Long groupId,
      String title,
      BuncheolStatus status,
      ShippingFeePolicy shippingFeePolicy) {
    return buncheol(id, groupId, title, status, shippingFeePolicy, FlowType.LEGACY);
  }

  private Buncheol buncheol(
      Long id,
      Long groupId,
      String title,
      BuncheolStatus status,
      ShippingFeePolicy shippingFeePolicy,
      FlowType flowType) {
    return buncheol(id, groupId, title, status, shippingFeePolicy, flowType, DEADLINE);
  }

  private Buncheol buncheol(
      Long id,
      Long groupId,
      String title,
      BuncheolStatus status,
      ShippingFeePolicy shippingFeePolicy,
      FlowType flowType,
      Instant deadline) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "flowType", flowType);
    setField(buncheol, "id", id);
    setField(buncheol, "hostId", HOST_ID);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "description", "분철 설명");
    setField(buncheol, "purchaseSite", "https://store.example.com");
    setField(buncheol, "deadline", deadline);
    setField(buncheol, "minHeadcount", 3);
    setField(buncheol, "status", status);
    setField(buncheol, "shippingFeePolicy", shippingFeePolicy);
    return buncheol;
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId) {
    return buncheolMember(id, buncheolId, memberId, 50_000L);
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId, long price) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", buncheolId);
    setField(member, "memberId", memberId);
    setField(member, "price", price);
    return member;
  }

  private BuncheolImage image(Long id, String url) {
    return image(id, url, false);
  }

  private BuncheolImage image(Long id, String url, boolean thumbnail) {
    BuncheolImage image = newInstance(BuncheolImage.class);
    setField(image, "id", id);
    setField(image, "imageUrl", url);
    setField(image, "thumbnail", thumbnail);
    return image;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private GroupMember groupMember(Long id, String name, String image) {
    GroupMember member = newInstance(GroupMember.class);
    setField(member, "id", id);
    setField(member, "name", name);
    setField(member, "image", image);
    return member;
  }

  private Participation active(
      Long id, Long buncheolMemberId, Long participantId, ParticipationStatus status) {
    Participation p = newInstance(Participation.class);
    setField(p, "id", id);
    setField(p, "buncheolId", BUNCHEOL_ID);
    setField(p, "buncheolMemberId", buncheolMemberId);
    setField(p, "participantId", participantId);
    setField(p, "status", status);
    setField(p, "dueAt", PAYMENT_DUE_AT);
    return p;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = findField(target.getClass(), fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}

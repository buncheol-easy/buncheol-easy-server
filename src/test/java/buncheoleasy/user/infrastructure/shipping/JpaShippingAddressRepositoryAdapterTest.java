package buncheoleasy.user.infrastructure.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaShippingAddressRepositoryAdapter 테스트")
class JpaShippingAddressRepositoryAdapterTest {

  @Autowired private ShippingAddressRepository shippingAddressRepository;

  @Autowired private UserRepository userRepository;

  @PersistenceContext private EntityManager em;

  private Long userId;

  @BeforeEach
  void setUp() {
    User user = User.create("KAKAO", "123456", "test@example.com");
    userRepository.save(user);
    em.flush();
    em.clear();
    userId = user.getId();
  }

  private ShippingAddress saveAndReload(ShippingAddress address) {
    shippingAddressRepository.save(address);
    em.flush();
    em.clear();
    return address;
  }

  @Nested
  @DisplayName("배송지 저장 테스트")
  class SaveTest {

    @Test
    void 배송지를_저장하면_ID가_할당된다() {
      // given
      ShippingAddress address = ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점");

      // when
      shippingAddressRepository.save(address);
      em.flush();

      // then
      assertThat(address.getId()).isNotNull();
      assertThat(address.getId()).isPositive();
    }

    @Test
    void 동일한_유저의_다른_배송지를_여러_개_저장할_수_있다() {
      // given
      ShippingAddress address1 = ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점");
      ShippingAddress address2 = ShippingAddress.create(userId, "CU_HALF", "CU 홍대입구점");

      // when
      shippingAddressRepository.save(address1);
      shippingAddressRepository.save(address2);
      em.flush();

      // then
      assertThat(address1.getId()).isNotEqualTo(address2.getId());
    }
  }

  @Nested
  @DisplayName("Id로 배송지 조회 테스트")
  class FindByIdTest {

    @Test
    void ID로_배송지를_조회할_수_있다() {
      // given
      ShippingAddress address =
          saveAndReload(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));

      // when
      Optional<ShippingAddress> found = shippingAddressRepository.findById(address.getId());

      // then
      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(address.getId());
      assertThat(found.get().getUserId()).isEqualTo(userId);
      assertThat(found.get().getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(found.get().getStoreName()).isEqualTo("GS25 강남역점");
    }

    @Test
    void 존재하지_않는_ID로_조회하면_empty를_반환한다() {
      // when
      Optional<ShippingAddress> found = shippingAddressRepository.findById(999999L);

      // then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("유저의 배송지 목록 조회 테스트")
  class GetUserShippingAddressesTest {

    @Test
    void userId로_배송지_목록을_조회할_수_있다() {
      // given
      shippingAddressRepository.save(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      shippingAddressRepository.save(ShippingAddress.create(userId, "CU_HALF", "CU 홍대입구점"));
      em.flush();
      em.clear();

      // when
      List<ShippingAddress> addresses = shippingAddressRepository.getUserShippingAddresses(userId);

      // then
      assertThat(addresses).hasSize(2);
      assertThat(addresses).extracting(ShippingAddress::getUserId).containsOnly(userId);
    }

    @Test
    void 다른_유저의_배송지는_조회되지_않는다() {
      // given
      User otherUser = User.create("KAKAO", "other_user", "other@example.com");
      userRepository.save(otherUser);

      shippingAddressRepository.save(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      shippingAddressRepository.save(
          ShippingAddress.create(otherUser.getId(), "CU_HALF", "CU 홍대입구점"));
      em.flush();
      em.clear();

      // when
      List<ShippingAddress> addresses = shippingAddressRepository.getUserShippingAddresses(userId);

      // then
      assertThat(addresses).hasSize(1);
      assertThat(addresses.getFirst().getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
    }

    @Test
    void 배송지가_없으면_빈_리스트를_반환한다() {
      // when
      List<ShippingAddress> addresses = shippingAddressRepository.getUserShippingAddresses(userId);

      // then
      assertThat(addresses).isEmpty();
    }
  }

  @Nested
  @DisplayName("배송지 수정 테스트")
  class UpdateTest {

    @Test
    void 배송지_정보를_업데이트할_수_있다() {
      // given
      ShippingAddress address =
          saveAndReload(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      ShippingAddress managed = shippingAddressRepository.findById(address.getId()).orElseThrow();
      managed.update("CU_HALF", "CU 홍대입구점");

      // when
      shippingAddressRepository.update(managed);
      em.flush();
      em.clear();

      // then
      ShippingAddress updated = shippingAddressRepository.findById(address.getId()).orElseThrow();
      assertThat(updated.getShippingMethod()).isEqualTo(ShippingMethod.CU_HALF);
      assertThat(updated.getStoreName()).isEqualTo("CU 홍대입구점");
    }
  }

  @Nested
  @DisplayName("배송지 삭제 테스트")
  class DeleteTest {

    @Test
    void 배송지를_삭제할_수_있다() {
      // given
      ShippingAddress address =
          saveAndReload(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      Long addressId = address.getId();

      // when
      shippingAddressRepository.delete(addressId);
      em.flush();
      em.clear();

      // then
      assertThat(shippingAddressRepository.findById(addressId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("유저 배송지 목록 개수 조회 테스트")
  class CountByUserIdTest {

    @Test
    void userId로_배송지_개수를_조회할_수_있다() {
      // given
      shippingAddressRepository.save(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      shippingAddressRepository.save(ShippingAddress.create(userId, "CU_HALF", "CU 홍대입구점"));
      em.flush();

      // when
      int count = shippingAddressRepository.countByUserId(userId);

      // then
      assertThat(count).isEqualTo(2);
    }

    @Test
    void 다른_유저의_배송지는_카운트되지_않는다() {
      // given
      User otherUser = User.create("KAKAO", "other_user", "other@example.com");
      userRepository.save(otherUser);
      shippingAddressRepository.save(
          ShippingAddress.create(otherUser.getId(), "GS25_HALF", "GS25 강남역점"));
      em.flush();

      // when: userId 기준으로 카운트
      int count = shippingAddressRepository.countByUserId(userId);

      // then
      assertThat(count).isZero();
    }
  }

  @Nested
  @DisplayName("유저의 동일한 배송지 존재 여부 확인 테스트")
  class ExistsTest {

    @Test
    void 동일한_배송지가_존재하면_true를_반환한다() {
      // given
      shippingAddressRepository.save(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      em.flush();

      // when
      boolean exists =
          shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
              userId, "GS25_HALF", "GS25 강남역점");

      // then
      assertThat(exists).isTrue();
    }

    @Test
    void 동일한_배송지가_없으면_false를_반환한다() {
      // when
      boolean exists =
          shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
              userId, "GS25_HALF", "GS25 강남역점");

      // then
      assertThat(exists).isFalse();
    }

    @Test
    void 배송방법이_다르면_false를_반환한다() {
      // given
      shippingAddressRepository.save(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      em.flush();

      // when
      boolean exists =
          shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
              userId, "CU_HALF", "GS25 강남역점");

      // then
      assertThat(exists).isFalse();
    }

    @Test
    void 지점명이_다르면_false를_반환한다() {
      // given
      shippingAddressRepository.save(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점"));
      em.flush();

      // when
      boolean exists =
          shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
              userId, "GS25_HALF", "GS25 신촌역점");

      // then
      assertThat(exists).isFalse();
    }

    @Test
    void 다른_유저의_동일한_배송지는_해당_유저의_중복으로_처리되지_않는다() {
      // given
      User otherUser = User.create("KAKAO", "other_user", "other@example.com");
      userRepository.save(otherUser);
      shippingAddressRepository.save(
          ShippingAddress.create(otherUser.getId(), "GS25_HALF", "GS25 강남역점"));
      em.flush();

      // when: userId 기준으로 조회
      boolean exists =
          shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
              userId, "GS25_HALF", "GS25 강남역점");

      // then
      assertThat(exists).isFalse();
    }
  }
}

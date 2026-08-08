package buncheoleasy.user.domain.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 서비스 + 실제 JPA 조합 검증. clearDefault 벌크 UPDATE 의 clearAutomatically 가 영속성 컨텍스트를
 * 비운 뒤에도 수정 대상 엔티티의 변경이 DB 에 반영되는지는 Mockito 단위 테스트로는 잡을 수 없어 통합으로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ShippingAddressDomainService 통합 테스트")
class ShippingAddressDomainServiceIntegrationTest {

  @Autowired private ShippingAddressDomainService shippingAddressDomainService;

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

  private ShippingAddress saveAndDetach(ShippingAddress address) {
    shippingAddressRepository.save(address);
    em.flush();
    em.clear();
    return address;
  }

  private ShippingAddress reload(Long id) {
    return shippingAddressRepository.findById(id).orElseThrow();
  }

  @Nested
  @DisplayName("수정으로 기본 배송지 지정 테스트")
  class UpdateDefaultTest {

    @Test
    void 수정으로_기본_배송지를_지정하면_대상은_true_기존_기본은_false로_저장된다() {
      // given: 같은 method 의 기존 기본 배송지 1개 + 일반 배송지 1개
      ShippingAddress previousDefault =
          saveAndDetach(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점", null, true));
      ShippingAddress target =
          saveAndDetach(ShippingAddress.create(userId, "GS25_HALF", "GS25 신촌역점", null, false));

      // when: 기본 배송지 지정 수정 — clearDefault 벌크 UPDATE 가 영속성 컨텍스트를 비우는 경로
      shippingAddressDomainService.updateShippingAddress(
          userId, target.getId(), "GS25_HALF", "GS25 신촌역점", null, true);
      em.flush();
      em.clear();

      // then: 대상이 기본 배송지가 되고, 기존 기본은 해제된다
      assertThat(reload(target.getId()).isDefault()).isTrue();
      assertThat(reload(previousDefault.getId()).isDefault()).isFalse();
    }

    @Test
    void 다른_method의_기본_배송지는_영향받지_않는다() {
      // given
      ShippingAddress otherMethodDefault =
          saveAndDetach(ShippingAddress.create(userId, "CU_HALF", "CU 홍대점", null, true));
      ShippingAddress target =
          saveAndDetach(ShippingAddress.create(userId, "GS25_HALF", "GS25 신촌역점", null, false));

      // when
      shippingAddressDomainService.updateShippingAddress(
          userId, target.getId(), "GS25_HALF", "GS25 신촌역점", null, true);
      em.flush();
      em.clear();

      // then
      assertThat(reload(target.getId()).isDefault()).isTrue();
      assertThat(reload(otherMethodDefault.getId()).isDefault()).isTrue();
    }
  }

  @Nested
  @DisplayName("등록으로 기본 배송지 지정 테스트")
  class CreateDefaultTest {

    @Test
    void 기본_배송지로_등록하면_true로_저장되고_같은_method의_기존_기본은_해제된다() {
      // given
      ShippingAddress previousDefault =
          saveAndDetach(ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점", null, true));

      // when
      ShippingAddress created =
          shippingAddressDomainService.createShippingAddress(
              userId, "GS25_HALF", "GS25 신촌역점", null, true);
      em.flush();
      em.clear();

      // then
      assertThat(reload(created.getId()).isDefault()).isTrue();
      assertThat(reload(previousDefault.getId()).isDefault()).isFalse();
    }
  }
}

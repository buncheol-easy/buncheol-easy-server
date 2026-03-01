package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("BuncheolStatus 도메인 테스트")
class BuncheolStatusTest {

    @Nested
    @DisplayName("취소 가능 여부 테스트")
    class IsCancellableTest {

        @ParameterizedTest
        @EnumSource(value = BuncheolStatus.class,
                names = {"RECRUITING", "CLOSED", "GOODS_ORDERED", "SELLER_SHIPPING"})
        void 취소_가능_상태이면_true를_반환한다(BuncheolStatus status) {
            assertThat(status.isCancellable()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = BuncheolStatus.class,
                names = {"HOST_SHIPPING", "ALL_RECEIVED", "SETTLING", "SETTLED", "FINISHED", "CANCELLED"})
        void 취소_불가_상태이면_false를_반환한다(BuncheolStatus status) {
            assertThat(status.isCancellable()).isFalse();
        }
    }
}

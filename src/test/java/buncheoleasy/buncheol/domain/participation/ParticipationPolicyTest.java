package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ParticipationPolicy 테스트")
class ParticipationPolicyTest {

  @ParameterizedTest
  @CsvSource({"1000, 1000", "3000, 3000", "5000, 5000", "5001, 5000", "10000, 5000", "50000, 5000"})
  void 제시_예치금은_제시_금액과_최대_예치금_중_작은_값이다(long bidAmount, long expectedDeposit) {
    // when
    long deposit = ParticipationPolicy.resolveBidDepositAmount(bidAmount);

    // then
    assertThat(deposit).isEqualTo(expectedDeposit);
  }

  @Test
  void 최대_예치금_상수는_5000이다() {
    assertThat(ParticipationPolicy.MAX_BID_DEPOSIT_AMOUNT).isEqualTo(5_000L);
  }
}

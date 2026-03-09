package buncheoleasy.buncheol.domain.participation;

public final class ParticipationPolicy {

  public static final long MAX_BID_DEPOSIT_AMOUNT = 5_000L;

  private ParticipationPolicy() {}

  public static long resolveBidDepositAmount(final long bidAmount) {
    return Math.min(bidAmount, MAX_BID_DEPOSIT_AMOUNT);
  }
}

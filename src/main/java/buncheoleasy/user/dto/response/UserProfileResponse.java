package buncheoleasy.user.dto.response;

public record UserProfileResponse(
    String provider,
    String email,
    String nickname,
    String name,
    String phoneNumber,
    BankAccountInfo bankAccount,
    boolean canHost) {

  public static UserProfileResponse of(
      final String provider,
      final String email,
      final String nickname,
      final String name,
      final String phoneNumber,
      final BankAccountInfo bankAccount,
      final boolean canHost) {
    return new UserProfileResponse(
        provider, email, nickname, name, phoneNumber, bankAccount, canHost);
  }

  public record BankAccountInfo(String bank, String account, String holder) {}
}

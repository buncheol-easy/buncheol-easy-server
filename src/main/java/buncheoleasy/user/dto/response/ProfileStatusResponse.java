package buncheoleasy.user.dto.response;

public record ProfileStatusResponse(boolean profileCompleted) {

  public static ProfileStatusResponse of(final boolean profileCompleted) {
    return new ProfileStatusResponse(profileCompleted);
  }
}

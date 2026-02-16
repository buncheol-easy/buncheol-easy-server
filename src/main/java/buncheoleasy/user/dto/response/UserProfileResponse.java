package buncheoleasy.user.dto.response;

public record UserProfileResponse(
        String provider,
        String email,
        String nickname,
        String phoneNumber
) {
    public static UserProfileResponse of(final String provider, final String email, final String nickname,
                                         final String phoneNumber) {
        return new UserProfileResponse(provider, email, nickname, phoneNumber);
    }
}

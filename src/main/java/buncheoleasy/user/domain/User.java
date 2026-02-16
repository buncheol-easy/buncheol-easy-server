package buncheoleasy.user.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class User {

    private static final String NICKNAME_PREFIX = "Guest";
    private static final int RANDOM_SUFFIX_LENGTH = 10;

    private Long id;
    private final SocialInfo socialInfo;
    private final Email email;
    private Nickname nickname;
    private PhoneNumber phoneNumber;
    private boolean profileCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public User(final Long id, final String provider, final String providerId,
                final String nickname, final String email, final String phoneNumber,
                final Boolean profileCompleted, final LocalDateTime createdAt, final LocalDateTime updatedAt,
                final LocalDateTime deletedAt) {
        this.id = id;
        this.socialInfo = SocialInfo.of(provider, providerId);
        this.nickname = Nickname.of(nickname);
        this.email = Email.of(email);
        this.phoneNumber = phoneNumber != null ? new PhoneNumber(phoneNumber) : null;
        this.profileCompleted = profileCompleted != null ? profileCompleted : false;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static User create(final String provider, final String providerId,
                              final String email) {
        return new User(provider, providerId, email);
    }

    private User(final String provider, final String providerId, final String email) {
        this.socialInfo = SocialInfo.of(provider, providerId);
        this.email = Email.of(email);
        this.nickname = Nickname.of(generateRandomNickname());
        this.profileCompleted = false;
    }

    private String generateRandomNickname() {
        String cleanUuid = UUID.randomUUID().toString().replace("-", "");
        String uniqueSuffix = cleanUuid.substring(0, RANDOM_SUFFIX_LENGTH);
        return NICKNAME_PREFIX + uniqueSuffix;
    }

    public void updatePhoneNumber(final String newValue) {
        PhoneNumber newPhoneNumber = PhoneNumber.of(newValue);

        boolean wasNull = (this.phoneNumber == null);
        this.phoneNumber = newPhoneNumber;

        // 최초 전화번호 설정 시에만 profileCompleted를 true로 변경
        if (wasNull && !this.profileCompleted) {
            this.profileCompleted = true;
        }
    }

    public void updateNickname(final String newValue) {
        this.nickname = Nickname.of(newValue);
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }
}

package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public User(final Long id, final String provider, final String providerId,
                final String nickname, final String email, final String phoneNumber,
                final LocalDateTime createdAt, final LocalDateTime updatedAt,
                final LocalDateTime deletedAt) {
        this.id = id;
        this.socialInfo = SocialInfo.of(provider, providerId);
        this.nickname = Nickname.of(nickname);
        this.email = Email.of(email);
        this.phoneNumber = phoneNumber != null ? new PhoneNumber(phoneNumber) : null;
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
    }

    private String generateRandomNickname() {
        String cleanUuid = UUID.randomUUID().toString().replace("-", "");
        String uniqueSuffix = cleanUuid.substring(0, RANDOM_SUFFIX_LENGTH);
        return NICKNAME_PREFIX + uniqueSuffix;
    }

    public void updatePhoneNumber(final String newValue) {
        PhoneNumber newPhoneNumber = PhoneNumber.of(newValue);
        if (phoneNumber != null && phoneNumber.equals(newPhoneNumber)) {
            throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_UNCHANGED);
        }
        this.phoneNumber = newPhoneNumber;
    }

    public void updateNickname(final String newValue) {
        Nickname newNickname = Nickname.of(newValue);
        if (nickname != null && nickname.equals(newNickname)) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_UNCHANGED);
        }
        this.nickname = newNickname;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }
}

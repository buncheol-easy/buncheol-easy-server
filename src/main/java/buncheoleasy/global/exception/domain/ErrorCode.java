package buncheoleasy.global.exception.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /**
     * USR - 유저 관련 에러
     */
    USER_SOCIAL_ID_REQUIRED("USR-001", "소셜 고유 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    USER_SOCIAL_ID_LENGTH_INVALID("USR-002", "소셜 고유 ID는 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    USER_SOCIAL_ID_FORMAT_INVALID("USR-003", "소셜 고유 ID는 영문자, 숫자, 언더스코어(_), 하이픈(-)만 사용 가능합니다.", HttpStatus.BAD_REQUEST),

    USER_NICKNAME_REQUIRED("USR-004", "닉네임은 필수입니다.", HttpStatus.BAD_REQUEST),
    USER_NICKNAME_LENGTH_INVALID("USR-005", "닉네임은 1자 이상 20자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    USER_NICKNAME_FORMAT_INVALID("USR-006", "닉네임은 한글, 영문자, 숫자만 사용 가능합니다.", HttpStatus.BAD_REQUEST),

    USER_EMAIL_REQUIRED("USR-007", "이메일은 필수입니다.", HttpStatus.BAD_REQUEST),
    USER_EMAIL_LENGTH_INVALID("USR-008", "이메일은 320자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    USER_EMAIL_FORMAT_INVALID("USR-009", "올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST),

    USER_PHONE_NUMBER_REQUIRED("USR-010", "전화번호는 필수입니다.", HttpStatus.BAD_REQUEST),
    USER_PHONE_NUMBER_LENGTH_INVALID("USR-011", "전화번호는 10자 또는 11자여야 합니다.", HttpStatus.BAD_REQUEST),
    USER_PHONE_NUMBER_FORMAT_INVALID("USR-012", "올바른 전화번호 형식이 아닙니다. (예: 01012345678)", HttpStatus.BAD_REQUEST),

    USER_NICKNAME_UNCHANGED("USR-013", "변경할 닉네임이 현재 닉네임과 동일합니다.", HttpStatus.BAD_REQUEST),
    USER_PHONE_NUMBER_UNCHANGED("USR-014", "변경할 전화번호가 현재 전화번호와 동일합니다.", HttpStatus.BAD_REQUEST),
    USER_NICKNAME_DUPLICATE("USR-015", "이미 다른 사용자가 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),

    PROVIDER_REQUIRED("USR-016", "소셜 로그인 제공자는 필수입니다.", HttpStatus.BAD_REQUEST),
    PROVIDER_NOT_FOUND("USR-017", "지원하지 않는 소셜 로그인 제공자입니다.", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("USR-018", "존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND),

    SHIPPING_METHOD_FORMAT_INVALID("USR-018", "올바른 배송방법 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
    USER_PROFILE_IS_NOT_COMPLETE("USR-019", "사용자의 프로필 설정이 완료되지 않았습니다.", HttpStatus.BAD_REQUEST),

    /**
     * AUTH - 인증 관련 에러
     */
    AUTH_UNSUPPORTED_AUTHENTICATION("AUTH-001", "지원하지 않는 인증 타입입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_SOCIAL_PROVIDER_UNSUPPORTED("AUTH-002", "지원하지 않는 소셜 로그인 제공자입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_SOCIAL_EMAIL_REQUIRED("AUTH-003", "소셜 계정 이메일 정보가 없습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_OAUTH2_LOGIN_FAILED("AUTH-004", "소셜 로그인에 실패했습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_TOKEN("AUTH-005", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_EXPIRED_TOKEN("AUTH-006", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),

    /**
     * C - 공통 에러
     */
    INVALID_INPUT_VALUE("C-001", "적절하지 않은 입력값입니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("C-002", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    public ProblemDetail toProblemDetail() {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(httpStatus, message);
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}

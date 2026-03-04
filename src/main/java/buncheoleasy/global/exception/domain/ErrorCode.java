package buncheoleasy.global.exception.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  /** USR - 유저 관련 에러 */
  USER_SOCIAL_ID_REQUIRED("USR-001", "소셜 고유 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
  USER_SOCIAL_ID_LENGTH_INVALID("USR-002", "소셜 고유 ID는 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
  USER_SOCIAL_ID_FORMAT_INVALID(
      "USR-003", "소셜 고유 ID는 영문자, 숫자, 언더스코어(_), 하이픈(-)만 사용 가능합니다.", HttpStatus.BAD_REQUEST),

  USER_NICKNAME_REQUIRED("USR-004", "닉네임은 필수입니다.", HttpStatus.BAD_REQUEST),
  USER_NICKNAME_LENGTH_INVALID("USR-005", "닉네임은 1자 이상 20자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
  USER_NICKNAME_FORMAT_INVALID("USR-006", "닉네임은 한글, 영문자, 숫자만 사용 가능합니다.", HttpStatus.BAD_REQUEST),

  USER_EMAIL_REQUIRED("USR-007", "이메일은 필수입니다.", HttpStatus.BAD_REQUEST),
  USER_EMAIL_LENGTH_INVALID("USR-008", "이메일은 320자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
  USER_EMAIL_FORMAT_INVALID("USR-009", "올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST),

  USER_PHONE_NUMBER_REQUIRED("USR-010", "전화번호는 필수입니다.", HttpStatus.BAD_REQUEST),
  USER_PHONE_NUMBER_LENGTH_INVALID("USR-011", "전화번호는 10자 또는 11자여야 합니다.", HttpStatus.BAD_REQUEST),
  USER_PHONE_NUMBER_FORMAT_INVALID(
      "USR-012", "올바른 전화번호 형식이 아닙니다. (예: 01012345678)", HttpStatus.BAD_REQUEST),

  USER_NICKNAME_DUPLICATE("USR-013", "이미 다른 사용자가 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),

  PROVIDER_REQUIRED("USR-014", "소셜 로그인 제공자는 필수입니다.", HttpStatus.BAD_REQUEST),
  PROVIDER_NOT_FOUND("USR-015", "지원하지 않는 소셜 로그인 제공자입니다.", HttpStatus.BAD_REQUEST),
  USER_NOT_FOUND("USR-016", "존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND),

  SHIPPING_METHOD_FORMAT_INVALID("USR-017", "올바른 배송방법 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
  USER_PROFILE_IS_NOT_COMPLETE("USR-018", "사용자의 프로필 설정이 완료되지 않았습니다.", HttpStatus.FORBIDDEN),

  SHIPPING_ADDRESS_NOT_FOUND("USR-019", "배송지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  SHIPPING_ADDRESS_LIMIT_EXCEEDED("USR-020", "배송지는 최대 10개까지 등록할 수 있습니다.", HttpStatus.BAD_REQUEST),
  SHIPPING_ADDRESS_DUPLICATE("USR-021", "이미 등록된 배송지입니다.", HttpStatus.CONFLICT),
  SHIPPING_ADDRESS_FORBIDDEN("USR-022", "본인의 배송지만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN),

  /** AUTH - 인증 관련 에러 */
  AUTH_UNSUPPORTED_AUTHENTICATION("AUTH-001", "지원하지 않는 인증 타입입니다.", HttpStatus.UNAUTHORIZED),
  AUTH_SOCIAL_PROVIDER_UNSUPPORTED("AUTH-002", "지원하지 않는 소셜 로그인 제공자입니다.", HttpStatus.UNAUTHORIZED),
  AUTH_SOCIAL_EMAIL_REQUIRED("AUTH-003", "소셜 계정 이메일 정보가 없습니다.", HttpStatus.UNAUTHORIZED),
  AUTH_OAUTH2_LOGIN_FAILED("AUTH-004", "소셜 로그인에 실패했습니다.", HttpStatus.UNAUTHORIZED),
  AUTH_INVALID_TOKEN("AUTH-005", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
  AUTH_EXPIRED_TOKEN("AUTH-006", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),

  /** FILE - MultiPart File 관련 에러 */
  FILE_NAME_INVALID("FILE-001", "유효하지 않은 파일 이름입니다.", HttpStatus.BAD_REQUEST),
  FILE_EXTENSION_INVALID("FILE-002", "지원하지 않는 이미지 형식입니다.", HttpStatus.BAD_REQUEST),
  FILE_READ_FAILED("FILE-003", "파일을 읽는데 실패했습니다.", HttpStatus.BAD_REQUEST),

  /** BCH - 분철 관련 에러 */
  BUNCHEOL_REQUIRED_FIELD_MISSING("BCH-001", "분철 필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_TEXT_LENGTH_INVALID("BCH-002", "분철 텍스트 길이가 허용 범위를 초과했습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_DEADLINE_INVALID("BCH-003", "분철 마감일은 현재 시각 이후여야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_SHIPPING_DEADLINE_DAYS_INVALID(
      "BCH-004", "발송 마감일수는 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_PRICE_INVALID("BCH-005", "가격은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_SHIPPING_FEE_REQUIRED("BCH-006", "배송비는 최소 1개 이상 입력해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_SHIPPING_FEE_INVALID("BCH-007", "배송비는 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID(
      "BCH-008", "정산 정보 길이가 허용 범위를 초과했습니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_MEMBER_REQUIRED("BCH-020", "분철 멤버는 최소 1명 이상 존재해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_DUPLICATED("BCH-021", "중복된 멤버가 포함되어 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_NAME_REQUIRED("BCH-022", "그룹을 직접 입력한 경우 멤버 이름은 필수입니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_NAME_LENGTH_INVALID(
      "BCH-023", "분철 멤버 이름은 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED(
      "BCH-024", "공식 그룹의 분철 멤버는 멤버 ID가 필수입니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_PRICE_INVALID("BCH-025", "분철 멤버 즉시 구매가는 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_BID_MIN_PRICE_REQUIRED(
      "BCH-026", "제시 허용 시 제시 최소 금액은 필수입니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID(
      "BCH-027", "제시 최소 금액은 즉시 구매가보다 작아야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_BID_MIN_PRICE_FORBIDDEN(
      "BCH-028", "제시 미허용 시 제시 최소 금액은 입력할 수 없습니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_IMAGE_LIMIT_EXCEEDED("BCH-040", "이미지는 최대 3개까지 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_URL_REQUIRED("BCH-041", "이미지 URL은 필수입니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_URL_LENGTH_INVALID("BCH-042", "이미지 URL은 500자 이하여야 합니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_NOT_FOUND("BCH-043", "존재하지 않는 분철입니다.", HttpStatus.NOT_FOUND),
  BUNCHEOL_NO_PERMISSION("BCH-044", "분철에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
  BUNCHEOL_CANCEL_NOT_ALLOWED("BCH-050", "현재 상태에서는 분철을 취소할 수 없습니다.", HttpStatus.CONFLICT),

  /** GRP - 그룹 관련 에러 */
  GROUP_NOT_FOUND("GRP-001", "존재하지 않는 그룹입니다.", HttpStatus.NOT_FOUND),
  GROUP_MEMBER_NOT_IN_GROUP("GRP-002", "해당 그룹에 속하지 않는 멤버입니다.", HttpStatus.BAD_REQUEST),

  /** S3 - 이미지 저장소 관련 에러 */
  S3_UPLOAD_FAILED("S3-001", "이미지 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  /** C - 공통 에러 */
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

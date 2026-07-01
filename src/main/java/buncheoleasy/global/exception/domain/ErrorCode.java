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

  USER_WITHDRAW_BLOCKED_BY_ACTIVE_BUNCHEOL(
      "USR-028", "진행 중인 분철이 있어 탈퇴할 수 없습니다.", HttpStatus.CONFLICT),
  USER_WITHDRAW_BLOCKED_BY_ACTIVE_PARTICIPATION(
      "USR-029", "진행 중인 분철 참여가 있어 탈퇴할 수 없습니다.", HttpStatus.CONFLICT),

  SHIPPING_ADDRESS_NOT_FOUND("USR-019", "배송지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  SHIPPING_ADDRESS_LIMIT_EXCEEDED("USR-020", "배송지는 최대 5개까지 등록할 수 있습니다.", HttpStatus.BAD_REQUEST),
  SHIPPING_ADDRESS_DUPLICATE("USR-021", "이미 등록된 배송지입니다.", HttpStatus.CONFLICT),
  SHIPPING_ADDRESS_FORBIDDEN("USR-022", "본인의 배송지만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN),

  USER_BANK_ACCOUNT_REQUIRED("USR-023", "정산 계좌 필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
  USER_BANK_ACCOUNT_LENGTH_INVALID(
      "USR-024", "정산 계좌 정보 길이가 허용 범위를 초과했습니다.", HttpStatus.BAD_REQUEST),
  USER_BANK_ACCOUNT_NOT_REGISTERED("USR-025", "정산 계좌가 등록되어 있지 않습니다.", HttpStatus.CONFLICT),
  USER_BANK_ACCOUNT_FORMAT_INVALID("USR-026", "정산 계좌번호는 숫자만 입력 가능합니다.", HttpStatus.BAD_REQUEST),

  SHIPPING_ADDRESS_ALIAS_TOO_LONG("USR-027", "배송지 별칭은 10자 이하여야 합니다.", HttpStatus.BAD_REQUEST),

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
  BUNCHEOL_DEADLINE_NOT_ON_THE_HOUR(
      "BCH-004", "분철 마감 시간은 정각(매시 0분 0초)이어야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MIN_HEADCOUNT_INVALID("BCH-008", "분철 진행 최소 인원은 1명 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_SHIPPING_FEE_REQUIRED("BCH-006", "배송비는 최소 1개 이상 입력해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_SHIPPING_FEE_INVALID("BCH-007", "배송비는 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_MEMBER_REQUIRED("BCH-020", "분철 멤버는 최소 1명 이상 존재해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_DUPLICATED("BCH-021", "중복된 멤버가 포함되어 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_PRICE_INVALID(
      "BCH-027", "멤버 금액은 100원 단위의 0보다 큰 값이어야 합니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_IMAGE_LIMIT_EXCEEDED("BCH-040", "이미지는 최대 5개까지 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_URL_REQUIRED("BCH-041", "이미지 URL은 필수입니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_URL_LENGTH_INVALID("BCH-042", "이미지 URL은 500자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_REQUIRED("BCH-045", "이미지는 최소 1장 이상 등록해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_KEEP_IMAGE_INVALID(
      "BCH-046", "유지할 이미지 중 해당 분철의 이미지가 아닌 항목이 있습니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_NOT_FOUND("BCH-043", "존재하지 않는 분철입니다.", HttpStatus.NOT_FOUND),
  BUNCHEOL_NO_PERMISSION("BCH-044", "분철에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
  BUNCHEOL_CANCEL_NOT_ALLOWED("BCH-050", "현재 상태에서는 분철을 취소할 수 없습니다.", HttpStatus.CONFLICT),

  BUNCHEOL_NOT_RECRUITING("BCH-060", "모집 중인 분철이 아닙니다.", HttpStatus.CONFLICT),
  PARTICIPATION_MEMBER_NOT_FOUND("BCH-061", "해당 분철에 존재하지 않는 멤버입니다.", HttpStatus.NOT_FOUND),
  PARTICIPATION_REQUIRED_FIELD_MISSING("BCH-062", "참여 필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
  PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED(
      "BCH-065", "해당 배송 방법은 이 분철에서 지원하지 않습니다.", HttpStatus.BAD_REQUEST),
  PARTICIPATION_HOST_CANNOT_PARTICIPATE(
      "BCH-066", "주최자는 자신의 분철에 참여할 수 없습니다.", HttpStatus.FORBIDDEN),
  PARTICIPATION_STATE_TRANSITION_INVALID(
      "BCH-067", "현재 참여 상태에서는 요청한 작업을 수행할 수 없습니다.", HttpStatus.CONFLICT),
  PARTICIPATION_NOT_FOUND("BCH-068", "존재하지 않는 참여입니다.", HttpStatus.NOT_FOUND),
  PARTICIPATION_NO_PERMISSION("BCH-069", "해당 참여에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
  PARTICIPATION_ALREADY_EXISTS("BCH-070", "같은 멤버 슬롯에 이미 진행 중인 참여가 존재합니다.", HttpStatus.CONFLICT),
  PARTICIPATION_PAYMENT_DUE_PASSED("BCH-073", "입금 기한이 지났습니다.", HttpStatus.CONFLICT),
  PARTICIPATION_DUPLICATE_MEMBER("BCH-074", "같은 멤버 슬롯을 중복으로 선택했습니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_BOOKMARK_ALREADY_EXISTS("BCH-071", "이미 찜한 분철입니다.", HttpStatus.CONFLICT),
  BUNCHEOL_BOOKMARK_NOT_FOUND("BCH-072", "찜하지 않은 분철입니다.", HttpStatus.NOT_FOUND),

  /** DLV - 배송 관련 에러 */
  DELIVERY_SHIPPING_METHOD_REQUIRED("DLV-001", "배송 방법은 필수입니다.", HttpStatus.BAD_REQUEST),
  DELIVERY_STORE_NAME_REQUIRED("DLV-002", "편의점 지점명은 필수입니다.", HttpStatus.BAD_REQUEST),
  DELIVERY_RECEIVER_NICKNAME_REQUIRED("DLV-003", "수령인 닉네임은 필수입니다.", HttpStatus.BAD_REQUEST),
  DELIVERY_RECEIVER_PHONE_REQUIRED("DLV-004", "수령인 연락처는 필수입니다.", HttpStatus.BAD_REQUEST),
  DELIVERY_TRACKING_NUMBER_REQUIRED("DLV-005", "운송장 번호는 필수입니다.", HttpStatus.BAD_REQUEST),
  DELIVERY_NOT_FOUND("DLV-006", "배송 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  DELIVERY_STATE_TRANSITION_INVALID(
      "DLV-007", "현재 배송 상태에서는 해당 작업을 수행할 수 없습니다.", HttpStatus.CONFLICT),
  DELIVERY_NO_PERMISSION("DLV-008", "배송 정보에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),

  /** GRP - 그룹 관련 에러 */
  GROUP_NOT_FOUND("GRP-001", "존재하지 않는 그룹입니다.", HttpStatus.NOT_FOUND),
  GROUP_MEMBER_NOT_IN_GROUP("GRP-002", "해당 그룹에 속하지 않는 멤버입니다.", HttpStatus.BAD_REQUEST),
  FAVORITE_GROUP_ALREADY_EXISTS("GRP-003", "이미 최애로 등록된 그룹입니다.", HttpStatus.CONFLICT),
  FAVORITE_GROUP_NOT_FOUND("GRP-004", "최애로 등록하지 않은 그룹입니다.", HttpStatus.NOT_FOUND),
  FAVORITE_GROUP_LIMIT_EXCEEDED("GRP-005", "최애 그룹은 최대 5개까지 등록할 수 있습니다.", HttpStatus.CONFLICT),

  /** INB - 수신함(공지/알림) 관련 에러 */
  INBOX_MESSAGE_REQUIRED_FIELD_MISSING(
      "INB-001", "수신함 메시지 필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
  INBOX_MESSAGE_TEXT_LENGTH_INVALID(
      "INB-002", "수신함 메시지 텍스트 길이가 허용 범위를 초과했습니다.", HttpStatus.BAD_REQUEST),
  INBOX_MESSAGE_NOT_FOUND("INB-003", "존재하지 않는 수신함 메시지입니다.", HttpStatus.NOT_FOUND),
  INBOX_PIN_NOT_ALLOWED("INB-004", "공지만 상단 고정할 수 있습니다.", HttpStatus.CONFLICT),
  INBOX_LINK_PATH_INVALID(
      "INB-005", "연결 경로는 '//' 로 시작하지 않는 상대 경로(/...)여야 합니다.", HttpStatus.BAD_REQUEST),
  NOTICE_BANNER_INCOMPLETE(
      "INB-006", "배너는 제목과 이미지를 함께 입력해야 합니다.", HttpStatus.BAD_REQUEST),

  /** S3 - 이미지 저장소 관련 에러 */
  S3_UPLOAD_FAILED("S3-001", "이미지 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  /** PAGE - 페이지네이션 관련 에러 */
  CURSOR_INVALID("PAGE-001", "유효하지 않은 커서 값입니다.", HttpStatus.BAD_REQUEST),

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

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

  // ACTIVE 라는 이름은 API 하위호환으로 유지한다. 실제 판정은 배송·환급 종료까지 보는 "끝나지 않은(unfinished)"
  // 기준이며, 상세는 BuncheolRepository/ParticipationRepository 의 existsUnfinished* javadoc 참고.
  USER_WITHDRAW_BLOCKED_BY_ACTIVE_BUNCHEOL(
      "USR-028", "진행 중인 분철이 있어 탈퇴할 수 없습니다.", HttpStatus.CONFLICT),
  USER_WITHDRAW_BLOCKED_BY_ACTIVE_PARTICIPATION(
      "USR-029", "진행 중인 분철 참여가 있어 탈퇴할 수 없습니다.", HttpStatus.CONFLICT),
  SHIPPING_ADDRESS_DELETE_BLOCKED_BY_ACTIVE_PARTICIPATION(
      "USR-030", "진행 중인 참여가 사용 중인 배송지는 삭제할 수 없습니다.", HttpStatus.CONFLICT),

  SHIPPING_ADDRESS_NOT_FOUND("USR-019", "배송지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  SHIPPING_ADDRESS_LIMIT_EXCEEDED("USR-020", "배송지는 최대 5개까지 등록할 수 있습니다.", HttpStatus.BAD_REQUEST),
  SHIPPING_ADDRESS_DUPLICATE("USR-021", "이미 등록된 배송지입니다.", HttpStatus.CONFLICT),
  SHIPPING_ADDRESS_FORBIDDEN("USR-022", "본인의 배송지만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN),

  USER_BANK_ACCOUNT_REQUIRED("USR-023", "정산 계좌 필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
  USER_BANK_ACCOUNT_LENGTH_INVALID(
      "USR-024", "정산 계좌 정보 길이가 허용 범위를 초과했습니다.", HttpStatus.BAD_REQUEST),
  USER_BANK_ACCOUNT_NOT_REGISTERED("USR-025", "정산 계좌가 등록되어 있지 않습니다.", HttpStatus.CONFLICT),
  USER_BANK_ACCOUNT_FORMAT_INVALID(
      "USR-026", "계좌번호는 숫자와 하이픈(-)만 입력 가능합니다.", HttpStatus.BAD_REQUEST),

  SHIPPING_ADDRESS_ALIAS_TOO_LONG("USR-027", "배송지 별칭은 10자 이하여야 합니다.", HttpStatus.BAD_REQUEST),

  // LEGACY(운영진 방식) 개최는 운영이 지정한 계정(can_host)만 가능하다.
  USER_CANNOT_HOST("USR-031", "분철 개최 권한이 없습니다.", HttpStatus.FORBIDDEN),
  // C2C 개최 자격 게이트 (docs/46 §7.1-8 · docs/50). 연령대 미보유(재동의로 해결 가능 — 409)와
  // 미성년 확정(차단 — 403)을 나눠야 FE 가 "카카오 재동의 유도" 안내를 분기할 수 있다.
  USER_AGE_NOT_VERIFIED(
      "USR-032", "연령대 확인이 필요합니다. 카카오 로그인에서 연령대 제공에 동의해 주세요.", HttpStatus.CONFLICT),
  USER_NOT_ADULT("USR-033", "미성년자는 분철을 개최할 수 없습니다.", HttpStatus.FORBIDDEN),
  USER_BANK_ACCOUNT_TOO_SHORT(
      "USR-034", "계좌번호는 숫자 8자리 이상 입력해 주세요.", HttpStatus.BAD_REQUEST),
  // 회원 개최 오픈 전(서비스 스위치 off). 같은 성격의 선례인 배송비 환급 이벤트 off 가 409
  // (PAYBACK_NOT_ELIGIBLE) 라 계열을 맞춘다. 503 은 이 서비스에서 "진짜 장애" 와 1:1 로 쓰여 왔고
  // (5xx 는 S3 업로드 실패·내부 오류 둘뿐), 정상 사용자 동작인 개최 버튼 클릭이 5xx 를 만들면
  // 오픈 직전 트래픽이 몰리는 시점에 5xx 지표가 통째로 올라간다.
  C2C_HOSTING_NOT_OPEN(
      "USR-035", "회원 개최는 아직 오픈 전이에요. 준비되면 공지로 알려드릴게요.", HttpStatus.CONFLICT),

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
  BUNCHEOL_SHIPPING_FEE_INVALID("BCH-007", "배송비는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_MEMBER_REQUIRED("BCH-020", "분철 멤버는 최소 1명 이상 존재해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_DUPLICATED("BCH-021", "중복된 멤버가 포함되어 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_MEMBER_PRICE_INVALID(
      "BCH-027", "멤버 금액은 100원 단위의 0 이상인 값이어야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_LIMIT_EXCEEDED("BCH-040", "이미지는 최대 5개까지 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_URL_REQUIRED("BCH-041", "이미지 URL은 필수입니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_URL_LENGTH_INVALID("BCH-042", "이미지 URL은 500자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_IMAGE_REQUIRED("BCH-045", "이미지는 최소 1장 이상 등록해야 합니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_KEEP_IMAGE_INVALID(
      "BCH-046", "유지할 이미지 중 해당 분철의 이미지가 아닌 항목이 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_THUMBNAIL_INDEX_INVALID(
      "BCH-047", "대표사진 인덱스가 이미지 목록 범위를 벗어났습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_THUMBNAIL_IMAGE_INVALID(
      "BCH-048", "대표사진으로 지정한 이미지가 유지할 이미지 목록에 없습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_THUMBNAIL_SELECTION_DUPLICATED(
      "BCH-049", "대표사진은 유지 이미지와 신규 이미지 중 한 곳에서만 지정할 수 있습니다.", HttpStatus.BAD_REQUEST),
  BUNCHEOL_THUMBNAIL_REQUIRED("BCH-083", "대표사진 지정은 필수입니다.", HttpStatus.BAD_REQUEST),

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
  // BCH-074 는 다중 선택 시절의 중복 멤버 선택 에러로 배포됐다가 폐기된 번호라 재사용하지 않는다.
  PARTICIPATION_ALREADY_JOINED_BUNCHEOL(
      "BCH-075", "이미 참여 중인 분철입니다. 분철당 멤버 1명에만 참여할 수 있습니다.", HttpStatus.CONFLICT),

  // 배송비 환급(배송비 돌려받기, 오픈 이벤트)
  PAYBACK_NOT_ELIGIBLE("BCH-076", "배송비 환급 신청 대상이 아닙니다.", HttpStatus.CONFLICT),
  PAYBACK_STATE_TRANSITION_INVALID(
      "BCH-077", "현재 환급 상태에서는 요청한 작업을 수행할 수 없습니다.", HttpStatus.CONFLICT),
  PAYBACK_TWEET_URL_INVALID("BCH-078", "올바른 X(트위터) 트윗 URL 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
  PAYBACK_TWEET_URL_DUPLICATE(
      "BCH-079", "이미 다른 환급 신청에 사용된 트윗 URL 입니다.", HttpStatus.CONFLICT),
  PAYBACK_REFUND_ACCOUNT_MISSING("BCH-080", "환급받을 환불 계좌가 등록되어 있지 않습니다.", HttpStatus.CONFLICT),
  PAYBACK_REJECT_REASON_REQUIRED("BCH-081", "반려 사유는 필수입니다.", HttpStatus.BAD_REQUEST),

  BUNCHEOL_BOOKMARK_ALREADY_EXISTS("BCH-071", "이미 찜한 분철입니다.", HttpStatus.CONFLICT),
  BUNCHEOL_BOOKMARK_NOT_FOUND("BCH-072", "찜하지 않은 분철입니다.", HttpStatus.NOT_FOUND),

  // C2C 플로우 (docs/46) — 신청→확정→입금 직거래
  BUNCHEOL_FLOW_NOT_SUPPORTED(
      "BCH-084", "이 분철의 진행 방식에서는 지원하지 않는 요청입니다.", HttpStatus.CONFLICT),
  BUNCHEOL_CONFIRM_NOT_ALLOWED("BCH-085", "현재 상태에서는 성사 확정을 할 수 없습니다.", HttpStatus.CONFLICT),
  PARTICIPATION_CANCEL_NOT_ALLOWED(
      "BCH-086", "현재 상태에서는 참여를 취소할 수 없습니다. 고객센터로 문의해 주세요.", HttpStatus.CONFLICT),
  PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED(
      "BCH-087", "현재 상태에서는 '보냈어요' 처리를 할 수 없습니다.", HttpStatus.CONFLICT),
  BUNCHEOL_OPEN_CHAT_URL_INVALID(
      "BCH-088", "카카오 오픈채팅 링크 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
  // C2C 오픈으로 can_host 게이트가 사라진 자리의 남용 방지(무제한 개최·이미지 업로드) — 일반 유저 활성 개최 상한.
  BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED(
      "BCH-089", "동시에 진행할 수 있는 개최 수를 초과했습니다.", HttpStatus.CONFLICT),
  // 성사 확정(BCH-085)과 분리한다 — 개최자가 누른 건 "진행 확정"인데 "성사 확정" 실패라고 뜨면
  // 어느 단계에서 막혔는지 알 수 없다 (docs/53 Q-12, docs/54 4-1).
  BUNCHEOL_COLLECT_FINALIZE_NOT_ALLOWED(
      "BCH-090", "현재 상태에서는 진행 확정을 할 수 없습니다.", HttpStatus.CONFLICT),
  // 범용 플로우 가드(BCH-084)와 분리한다 — BCH-084 는 성사 확정·진행 확정·반려·보냈어요 등
  // C2C 전용 액션 6곳이 공유하므로, 취소 전용 안내를 그 자리에 넣으면 다른 액션에서 엉뚱한
  // 문구가 뜬다(이 PR 이 BCH-085 에 대해 고친 것과 같은 유형의 버그). docs/54 4-2.
  PARTICIPATION_CANCEL_NOT_SUPPORTED(
      "BCH-091",
      "이 분철에서는 직접 취소할 수 없어요. 입금 기한이 지나면 자동으로 취소돼요.",
      HttpStatus.CONFLICT),
  // 성사 확정을 거친 참여의 자발 취소 차단 (docs/56 H-09). 취소 불가 구간을 공유하는 BCH-086("고객센터로 문의")과
  // 분리한다 — 여기서 막힌 사람이 연락할 상대는 고객센터가 아니라 돈을 받을 개최자다(직거래).
  PARTICIPATION_CANCEL_AFTER_HOST_CONFIRM(
      "BCH-092",
      "개최자가 성사 확정한 뒤에는 참여를 직접 취소할 수 없어요. 사정이 생겼다면 개최자에게 먼저 알려 주세요.",
      HttpStatus.CONFLICT),
  // 입금 확인된 참여가 있는 분철의 개최자 취소 차단 (docs/56 H-13). 상태 위반(BCH-050)과 분리한다 — 상태는 취소 가능
  // 구간인데 "현재 상태에서는 취소할 수 없습니다" 만 뜨면 개최자가 무엇을 해야 하는지 알 수 없다.
  BUNCHEOL_CANCEL_CONFIRMED_PAYMENT_EXISTS(
      "BCH-093",
      "입금이 확인된 참여자가 있어 분철을 취소할 수 없어요. 받은 금액을 환불한 뒤 고객센터로 문의해 주세요.",
      HttpStatus.CONFLICT),
  // 전체 수정 가드(BCH-060)와 분리한다 — 링크 수정은 모집중이 끝난 뒤에도 열려 있어서
  // "모집 중인 분철이 아닙니다" 가 뜨면 실패 사유를 오해한다. 취소된 분철에서만 막힌다.
  BUNCHEOL_OPEN_CHAT_URL_NOT_EDITABLE(
      "BCH-094", "취소된 분철은 오픈채팅 링크를 수정할 수 없습니다.", HttpStatus.CONFLICT),

  // 참여 코드 — 사유별로 코드를 나눈다. 사용자가 해야 할 일이 다르기 때문이다(재발급 요청 / 문의 / 재입력).
  PARTICIPATION_CODE_REQUIRED("BCH-095", "이 슬롯은 참여 코드가 있어야 참여할 수 있어요.", HttpStatus.BAD_REQUEST),
  // 선착순 슬롯에 코드를 보내면 조용히 무시하지 않고 거부한다 — 무시하면 "코드를 넣었는데 엉뚱한 슬롯에 참여됐다" 를
  // 사후에 추적할 수 없다.
  PARTICIPATION_CODE_NOT_APPLICABLE("BCH-096", "이 슬롯은 코드 없이 참여할 수 있어요.", HttpStatus.BAD_REQUEST),
  // 형식 오류·미존재·타 슬롯 코드를 같은 코드로 응답한다. 특히 타 슬롯을 구분해 알리면, 남의 코드를 받은 사람이
  // "그 멤버로 가면 되는구나" 하고 실제로 그 슬롯을 점유해 버린다 — 발급 실수가 오배정으로 이어진다.
  PARTICIPATION_CODE_INVALID("BCH-097", "참여 코드를 다시 확인해 주세요.", HttpStatus.BAD_REQUEST),
  PARTICIPATION_CODE_EXPIRED(
      "BCH-098", "참여 코드가 만료되었습니다. 코드를 보내드린 곳으로 문의해 주세요.", HttpStatus.CONFLICT),
  PARTICIPATION_CODE_ALREADY_USED("BCH-099", "이미 사용된 참여 코드예요.", HttpStatus.CONFLICT),
  PARTICIPATION_CODE_REVOKED("BCH-100", "더 이상 사용할 수 없는 코드예요.", HttpStatus.CONFLICT),
  // 이하 운영자(어드민) 발급 경로 전용.
  PARTICIPATION_CODE_NOT_FOUND("BCH-101", "존재하지 않는 참여 코드입니다.", HttpStatus.NOT_FOUND),
  PARTICIPATION_CODE_MEMBER_ALREADY_ISSUED(
      "BCH-102", "이 슬롯에 아직 쓸 수 있는 코드가 있습니다. 재발급으로 이전 코드를 폐기하고 새로 발급하세요.", HttpStatus.CONFLICT),
  PARTICIPATION_CODE_MEMBER_NOT_CODE_ONLY("BCH-103", "코드 참여 슬롯이 아닙니다.", HttpStatus.CONFLICT),
  PARTICIPATION_CODE_EXPIRY_INVALID("BCH-104", "코드 유효기한은 현재 시각 이후여야 합니다.", HttpStatus.BAD_REQUEST),
  PARTICIPATION_CODE_REQUIRED_FIELD_MISSING(
      "BCH-105", "참여 코드 발급에 필요한 값이 누락되었습니다.", HttpStatus.BAD_REQUEST),
  PARTICIPATION_CODE_REVOKE_NOT_ALLOWED("BCH-106", "이미 사용되었거나 폐기된 코드입니다.", HttpStatus.CONFLICT),
  BUNCHEOL_MEMBER_ACCESS_TYPE_CHANGE_NOT_ALLOWED(
      "BCH-107", "참여자가 있는 슬롯의 접근 정책은 바꿀 수 없습니다.", HttpStatus.CONFLICT),
  PARTICIPATION_CODE_MEMBER_TAKEN(
      "BCH-108", "이미 참여자가 확정된 슬롯입니다. 발급해도 사용할 수 없어요.", HttpStatus.CONFLICT),
  // 코드 참여는 무상 제공(0원 + 배송비 면제)이 전제다. 유료 슬롯을 코드 참여로 만들면 화면은 "0원" 을
  // 안내하는데 서버는 유상 참여로 처리해 환불 계좌 누락(BCH-062)으로 실패한다.
  PARTICIPATION_CODE_MEMBER_NOT_FREE(
      "BCH-109", "코드 참여 슬롯은 0원이어야 합니다.", HttpStatus.CONFLICT),
  BUNCHEOL_C2C_CODE_MEMBER_NOT_ALLOWED(
      "BCH-110", "회원 개최(C2C) 분철에는 코드 참여 멤버를 만들 수 없습니다.", HttpStatus.CONFLICT),

  // ── 참여 묶음 ──
  // 「제외」는 미입금자를 정리하는 도구지 사람을 고르는 도구가 아니다 — 기한이 지나야 열린다 (docs/71 §8-1).
  BUNDLE_RELEASE_RECRUITING(
      "BCH-111", "모집 중에는 참여를 제외할 수 없어요. 입금 기한이 지난 뒤에 정리할 수 있어요.", HttpStatus.CONFLICT),
  BUNDLE_RELEASE_BEFORE_DUE(
      "BCH-112", "아직 입금 기한 전이에요. 기한이 지난 뒤에 제외할 수 있어요.", HttpStatus.CONFLICT),
  BUNDLE_RELEASE_HAS_CONFIRMED(
      "BCH-113", "입금이 확인된 참여는 제외할 수 없어요. 환불이 필요하면 고객센터로 문의해 주세요.", HttpStatus.CONFLICT),
  // 「제외」 전용이 아닌 범용 조회 실패다.
  BUNDLE_NOT_FOUND("BCH-114", "참여 묶음을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

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
  DELIVERY_BUNCHEOL_NOT_CONFIRMED(
      "DLV-009", "진행확정된 분철만 운송장을 등록할 수 있습니다.", HttpStatus.CONFLICT),

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

  /** ADM - 관리자 관련 에러 */
  // 인증된 토큰이지만 관리자 계정이 (더 이상) 없는 경우. 404 가 아니라 403 인 것은 의도 — 계정 존재 여부를 노출하지 않는다.
  ADMIN_NOT_FOUND("ADM-001", "관리자 권한이 없습니다.", HttpStatus.FORBIDDEN),
  // 아이디 없음/비밀번호 불일치를 구분하지 않는다 — 계정 존재 여부 열거(enumeration)를 막기 위함.
  ADMIN_LOGIN_FAILED("ADM-002", "아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
  // 무차별 대입 방어. 남은 시도 수·잠금 해제 시각을 알리지 않는다 — 공격자에게 재개 시점을 알려주는 셈이 된다.
  ADMIN_LOGIN_RATE_LIMITED(
      "ADM-003", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS),

  /** FDB - 의견 보내기 관련 에러 */
  // 비로그인도 열려 있는 엔드포인트라 도배 방지가 필요하다. 사용자에겐 사유를 자세히 알리지 않는다.
  FEEDBACK_RATE_LIMITED("FDB-001", "의견을 너무 자주 보냈어요. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS),

  /** S3 - 이미지 저장소 관련 에러 */
  S3_UPLOAD_FAILED("S3-001", "이미지 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  /** CVS - 편의점 접수처 관련 에러 */
  CVS_BRAND_INVALID("CVS-001", "지원하지 않는 편의점 브랜드입니다.", HttpStatus.BAD_REQUEST),

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

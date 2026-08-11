package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 관리자 ID/PW 로그인 요청. password 상한은 BCrypt 반영 한계(72바이트)에 맞췄다.
 *
 * <p>{@code loginId} 를 ASCII 영숫자·{@code ._-} 로 제한하는 이유는 두 가지다.
 *
 * <ol>
 *   <li><b>호출 제한 우회 차단</b> — {@code admins} 는 {@code utf8mb4_unicode_ci} 라 대소문자뿐
 *       아니라 <b>악센트도 무시</b>한다({@code 'a' = 'á'}). 제한 키를 자바에서 정규화하는 한
 *       {@code buncheol-ádmin} 같은 변형이 같은 계정을 찾으면서 별도 카운터를 쓰게 된다. 입력을
 *       ASCII 로 좁히면 {@code toLowerCase(Locale.ROOT)} 가 이 콜레이션과 등가가 된다.
 *   <li><b>로그 라인 위조 차단</b> — {@code @Size} 는 개행을 막지 못해 감사 로그에 임의 라인을
 *       주입할 수 있었다.
 * </ol>
 *
 * <p>⚠️ 부트스트랩 계정({@code ADMIN_LOGIN_ID})도 같은 제약을 만족해야 한다 —
 * {@code AdminAccountInitializer} 가 기동 시 검증한다.
 */
public record AdminLoginRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String loginId,
    @NotBlank @Size(max = 72) String password) {}

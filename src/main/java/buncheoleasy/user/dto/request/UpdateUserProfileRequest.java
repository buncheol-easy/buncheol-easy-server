package buncheoleasy.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
    @NotBlank @Size(min = 1, max = 20) @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$") String nickname,
    @NotBlank @Size(min = 10, max = 11) @Pattern(regexp = "^01[0-9]+$") String phoneNumber,
    // 실명 (입금 대조·배송 연락 참조). null 이면 기존 값을 유지한다 (실명 필드 없는 기존 호출 호환).
    @Size(min = 1, max = 30) @Pattern(regexp = "^[가-힣a-zA-Z]+$") String name,
    // 마케팅 정보 수신 동의 여부. null 이면 기존 동의 상태를 유지한다 (프로필만 수정하는 기존 호출 호환).
    Boolean marketingAgreed) {}

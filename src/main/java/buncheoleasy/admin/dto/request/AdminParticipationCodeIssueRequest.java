package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 참여 코드 발급 요청.
 *
 * @param issuedTo 코드를 보낸 계정(예: {@code X_handle}·{@code N_blogid}) — 운영 메모이며 인증에는 쓰지 않는다
 * @param validHours 유효기간(시간). 생략하면 48시간 — 발급 건마다 다르게 줄 수 있다
 * @param reissue true 면 이전 코드를 폐기한 뒤 발급. false 인데 아직 쓸 수 있는 코드가 있으면 거부한다
 */
public record AdminParticipationCodeIssueRequest(
    @NotNull Long buncheolMemberId,
    @Size(max = 50) String issuedTo,
    @Positive @Max(24 * 30) Integer validHours,
    boolean reissue) {}

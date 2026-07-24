package buncheoleasy.user.domain.serviceterm;

import java.time.Instant;

/** 카카오 간편가입 동의창에서 받은 약관 1건의 동의 상태 (동의 내역 조회 API 응답 단위). */
public record ServiceTermAgreement(String tag, boolean agreed, Instant agreedAt) {}

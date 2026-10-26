package buncheoleasy.auth.domain;

/** 애플 로그인 신원 토큰(identityToken) 검증. */
public interface AppleIdentityVerifier {

  /** 허용 앱(번들 ID)이 설정돼 애플 로그인을 받을 수 있는지. */
  boolean isEnabled();

  /**
   * 애플 서명·발급자·대상 앱·만료·nonce 를 확인한다. nonce 는 앱이 애플 로그인 요청에 넣은 값({@code SHA256(rawNonce)} 의 소문자
   * hex)과 토큰의 nonce 클레임이 같아야 한다.
   *
   * <p>보장 범위: 신원 토큰만 새어 나간 경우(로그·다른 앱에 넘긴 토큰 등)에는 rawNonce 가 없어 그 토큰으로 로그인하지 못한다. 토큰과 rawNonce 를
   * 함께 얻은 쪽은 토큰이 만료될 때까지(애플 기본 약 10분) 다시 쓸 수 있다 — 서버가 쓴 nonce 를 기억하지 않기 때문이다.
   */
  AppleIdentity verify(String identityToken, String rawNonce);
}

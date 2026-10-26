package buncheoleasy.auth.domain;

/** 애플 로그인 신원 토큰(identityToken) 검증. */
public interface AppleIdentityVerifier {

  /**
   * 애플 서명·발급자·대상 앱·만료·nonce 를 확인한다. nonce 는 앱이 애플 로그인 요청에 넣은 값({@code SHA256(rawNonce)} 의 소문자
   * hex)과 토큰의 nonce 클레임이 같아야 한다 — 다른 곳에서 얻은 토큰을 다시 쓰는 것을 막는다.
   */
  AppleIdentity verify(String identityToken, String rawNonce);
}

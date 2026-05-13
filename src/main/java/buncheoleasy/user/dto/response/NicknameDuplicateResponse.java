package buncheoleasy.user.dto.response;

public record NicknameDuplicateResponse(boolean duplicated) {

  public static NicknameDuplicateResponse of(final boolean duplicated) {
    return new NicknameDuplicateResponse(duplicated);
  }
}

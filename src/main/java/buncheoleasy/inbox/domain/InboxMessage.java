package buncheoleasy.inbox.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.Cursorable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수신함 메시지. 공지(NOTICE, 전체 대상)와 알림(NOTIFICATION, 특정 사용자 대상)을 단일 엔티티로 관리한다.
 *
 * <p>공지는 {@code recipientId == null}, 알림은 {@code recipientId != null} 이며 {@code pinned} 는 공지에만
 * 의미가 있다(알림은 항상 false). 정렬 기준 {@code (createdAt, id)} 은 {@link Cursorable} 로 노출해 커서 페이지네이션에
 * 사용한다.
 */
@Entity
@Table(name = "inbox_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessage extends TimestampedEntity implements Cursorable {

  private static final int TITLE_MAX_LENGTH = 200;
  private static final int REFERENCE_MAX_LENGTH = 200;
  private static final int DESCRIPTION_MAX_LENGTH = 5000;
  private static final int LINK_PATH_MAX_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private InboxMessageType type;

  // 알림(NOTIFICATION) 수신자 FK. 공지(NOTICE) 는 전체 대상이라 null.
  @Column(name = "recipient_id", updatable = false)
  private Long recipientId;

  @Column(nullable = false, length = 200)
  private String title;

  // 제목과 본문 설명 사이의 보조 텍스트(부제·맥락). 공지는 작성자 입력, 알림은 분철명 등.
  @Column(length = 200)
  private String reference;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private boolean pinned;

  // 연관 화면으로 이동하는 in-app 경로(예: /profile/bids). 연관 화면이 없으면 null.
  @Column(name = "link_path", length = 500)
  private String linkPath;

  public static InboxMessage createNotice(
      final String title,
      final String reference,
      final String description,
      final boolean pinned,
      final String linkPath) {
    return new InboxMessage(
        InboxMessageType.NOTICE, null, title, reference, description, pinned, linkPath);
  }

  public static InboxMessage createNotification(
      final Long recipientId,
      final String title,
      final String reference,
      final String description,
      final String linkPath) {
    validateRecipient(recipientId);
    return new InboxMessage(
        InboxMessageType.NOTIFICATION, recipientId, title, reference, description, false, linkPath);
  }

  private InboxMessage(
      final InboxMessageType type,
      final Long recipientId,
      final String title,
      final String reference,
      final String description,
      final boolean pinned,
      final String linkPath) {
    validateTitle(title);
    validateDescription(description);
    validateReference(reference);
    validateLinkPath(linkPath);
    this.type = type;
    this.recipientId = recipientId;
    this.title = title;
    this.reference = reference;
    this.description = description;
    this.pinned = pinned;
    this.linkPath = linkPath;
  }

  /** 공지는 모두에게, 알림은 수신자 본인에게만 보인다. */
  public boolean isVisibleTo(final Long userId) {
    return type == InboxMessageType.NOTICE || (userId != null && userId.equals(recipientId));
  }

  /** 상단 고정. 공지에만 의미가 있어 알림에는 허용하지 않는다. 이미 고정돼 있어도 멱등하게 처리한다. */
  public void pin() {
    validatePinnable();
    this.pinned = true;
  }

  /** 상단 고정 해제. 공지에만 허용한다. 이미 해제 상태여도 멱등하게 처리한다. */
  public void unpin() {
    validatePinnable();
    this.pinned = false;
  }

  // 알림 id 로 고정 시도 시 404 가 아니라 409 로 응답한다(메시지는 실재하나 종류가 고정 불가). 프로젝트의
  // `*_NOT_ALLOWED → CONFLICT` 컨벤션과 정합하며, 클라이언트에 "공지만 고정 가능"을 명시적으로 알린다.
  private void validatePinnable() {
    if (type != InboxMessageType.NOTICE) {
      throw new BusinessException(ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }
  }

  private static void validateRecipient(final Long recipientId) {
    if (recipientId == null) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }
  }

  private static void validateTitle(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > TITLE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }
  }

  private static void validateDescription(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > DESCRIPTION_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }
  }

  private static void validateReference(final String value) {
    if (value != null && value.length() > REFERENCE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }
  }

  private static void validateLinkPath(final String value) {
    if (value != null && value.length() > LINK_PATH_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }
  }
}

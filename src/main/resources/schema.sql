-- users 테이블 생성
CREATE TABLE IF NOT EXISTS users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL COMMENT '소셜 로그인 제공자 (KAKAO, GOOGLE, APPLE …)',
    provider_id         VARCHAR(100) NOT NULL COMMENT '소셜 제공자 고유 ID',
    email               VARCHAR(320) NOT NULL COMMENT '소셜 계정 이메일',
    nickname            VARCHAR(20)  NOT NULL COMMENT '닉네임',
    phone_number        VARCHAR(15)  NULL COMMENT '연락처',
    settlement_bank     VARCHAR(50)  NULL COMMENT '정산 은행',
    settlement_account  VARCHAR(50)  NULL COMMENT '정산 계좌번호',
    settlement_holder   VARCHAR(50)  NULL COMMENT '정산 예금주',
    profile_completed   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '프로필 설정 완료 여부',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          DATETIME     NULL COMMENT '회원탈퇴 soft delete',

    -- 유니크 제약조건을 위한 가상 컬럼 (deleted_at이 NULL이면 값을 갖고, 유저가 탈퇴하면 NULL이 된다)
    _active_provider    VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, provider, NULL)) VIRTUAL,
    _active_provider_id VARCHAR(100) GENERATED ALWAYS AS (IF(deleted_at IS NULL, provider_id, NULL)) VIRTUAL,
    _active_nickname    VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, nickname, NULL)) VIRTUAL,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_users_active_social_account (_active_provider, _active_provider_id),
    UNIQUE INDEX uq_users_active_nickname (_active_nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- shipping_addresses 테이블 생성
CREATE TABLE IF NOT EXISTS shipping_addresses
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    shipping_method VARCHAR(20)  NOT NULL COMMENT 'GS25_HALF | CU_HALF',
    store_name      VARCHAR(100) NOT NULL COMMENT '편의점 지점명',
    alias           VARCHAR(10)  NULL COMMENT '사용자 지정 별칭',
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '배송방법별 기본 배송지 여부',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_shipping_addresses_user_id (user_id),
    UNIQUE INDEX uq_shipping_addresses_user_method_store (user_id, shipping_method, store_name),

    CONSTRAINT fk_shipping_addresses_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- groups 테이블 생성
CREATE TABLE IF NOT EXISTS `groups`
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL COMMENT '그룹명',
    image      VARCHAR(500) NULL COMMENT '이미지 URL',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_groups_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- group_members 테이블 생성
CREATE TABLE IF NOT EXISTS group_members
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL COMMENT '멤버명',
    image      VARCHAR(500) NULL COMMENT '이미지 URL',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_group_members_group_id (group_id),
    INDEX idx_group_members_name (name),

    CONSTRAINT fk_group_members_group
        FOREIGN KEY (group_id)
            REFERENCES `groups` (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- buncheols 테이블 생성
CREATE TABLE IF NOT EXISTS buncheols
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    host_id           BIGINT       NOT NULL COMMENT '개최자',
    group_id          BIGINT       NOT NULL COMMENT '대상 그룹',
    title             VARCHAR(200) NOT NULL COMMENT '분철 제목',
    description       TEXT         NULL COMMENT '분철 설명',
    purchase_site     VARCHAR(200) NOT NULL COMMENT '구매처',
    deadline          DATETIME     NOT NULL COMMENT '분철 마감일',
    gs25_shipping_fee INT          NULL COMMENT 'GS25반값택배 배송비',
    cu_shipping_fee   INT          NULL COMMENT 'CU반값택배 배송비',
    status            VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING | CLOSED | PAID | SETTLING | FINISHED | CANCELLED',
    closed_at         DATETIME     NULL COMMENT '마감 일시',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheols_group_id (group_id),
    INDEX idx_buncheols_title (title),
    INDEX idx_buncheols_host_created (host_id, created_at DESC),
    -- 공개 목록 조회 (CANCELLED 제외, createdAt DESC 정렬) 의 커서 페이지네이션용
    INDEX idx_buncheols_status_created (status, created_at DESC, id DESC),
    -- groupId 필터 + 커서 조합용 (idx_buncheols_group_id 만으로는 정렬을 인덱스로 커버 불가)
    INDEX idx_buncheols_group_created (group_id, created_at DESC, id DESC),

    CONSTRAINT fk_buncheols_host
        FOREIGN KEY (host_id)
            REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheols_group
        FOREIGN KEY (group_id)
            REFERENCES `groups` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buncheol_members
(
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    buncheol_id   BIGINT   NOT NULL,
    member_id     BIGINT   NOT NULL COMMENT '대상 멤버',
    bid_min_price BIGINT   NOT NULL COMMENT '제시 최소 금액',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheol_members_buncheol_id (buncheol_id),
    UNIQUE INDEX uq_buncheol_members_buncheol_member (buncheol_id, member_id),
    -- memberId 단독 검색 (분철 목록의 memberId 서브쿼리) 인덱스. uq_(buncheol_id, member_id) 는 member_id 단독 검색에 무용.
    INDEX idx_buncheol_members_member (member_id, buncheol_id),

    CONSTRAINT fk_buncheol_members_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_members_member
        FOREIGN KEY (member_id)
            REFERENCES group_members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buncheol_images
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id BIGINT       NOT NULL,
    image_url   VARCHAR(500) NOT NULL COMMENT '이미지 URL',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheol_images_buncheol_id (buncheol_id),

    CONSTRAINT fk_buncheol_images_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participations
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id           BIGINT       NOT NULL,
    buncheol_member_id    BIGINT       NOT NULL COMMENT '참여 대상 멤버 슬롯',
    participant_id        BIGINT       NOT NULL COMMENT '참여자',
    shipping_address_id   BIGINT       NOT NULL COMMENT '신청 시 선택한 배송지',
    bid_amount            BIGINT       NOT NULL COMMENT '제시 금액',
    due_at                DATETIME     NULL COMMENT '낙찰자 결제 만료 시각 (UTC). 차순위 이양 시 갱신',
    closed_rank           INT          NULL COMMENT '마감 시점 제시 순위',
    fail_reason           VARCHAR(100) NULL COMMENT 'FAILED 사유',
    finalized_at          DATETIME     NULL COMMENT '참여 확정/실패 최종 확정 시각',
    status                VARCHAR(30)  NOT NULL COMMENT 'ACTIVE_BID | AWAITING_PAYMENT | CONFIRMED | CANCELLED | FAILED',
    confirmed_member_id   BIGINT GENERATED ALWAYS AS (
                              IF(status = 'CONFIRMED', buncheol_member_id, NULL)
                              ) STORED COMMENT 'CONFIRMED일 때만 buncheol_member_id 값',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_participations_buncheol_id (buncheol_id),
    active_participant_id BIGINT GENERATED ALWAYS AS (
                              IF(status IN ('ACTIVE_BID', 'AWAITING_PAYMENT', 'CONFIRMED'),
                                 participant_id, NULL)
                              ) STORED COMMENT '활성 상태일 때만 participant_id 값',

    UNIQUE INDEX uq_participations_active_member_participant (buncheol_member_id, active_participant_id),
    UNIQUE INDEX uq_participations_confirmed_member (confirmed_member_id),
    INDEX idx_participations_member_status (buncheol_member_id, status),
    INDEX idx_participations_participant_created (participant_id, created_at DESC),

    CONSTRAINT fk_participations_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE,

    CONSTRAINT fk_participations_buncheol_member
        FOREIGN KEY (buncheol_member_id)
            REFERENCES buncheol_members (id),

    CONSTRAINT fk_participations_user
        FOREIGN KEY (participant_id)
            REFERENCES users (id),

    CONSTRAINT fk_participations_shipping_address
        FOREIGN KEY (shipping_address_id)
            REFERENCES shipping_addresses (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id  BIGINT       NOT NULL COMMENT '참여 ID',
    tx_type           VARCHAR(20)  NOT NULL COMMENT 'PAYMENT | REFUND',
    order_id          VARCHAR(100) NOT NULL COMMENT '결제 주문 ID',
    payment_key       VARCHAR(200) NULL COMMENT '토스 결제 키',
    parent_payment_id BIGINT       NULL COMMENT 'REFUND(환불)가 참조하는 원 결제 레코드 ID',
    amount            BIGINT       NOT NULL COMMENT '거래 금액(원, 양수)',
    status            VARCHAR(20)  NOT NULL COMMENT 'PENDING | CONFIRMING | DONE | FAILED',
    reason            VARCHAR(255) NULL COMMENT '실패/환불 사유',
    requested_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at       DATETIME     NULL COMMENT '승인/환불 완료 시각',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_payments_participation_id (participation_id),
    INDEX idx_payments_tx_type_status (tx_type, status),
    UNIQUE INDEX uq_payments_order_id (order_id),
    UNIQUE INDEX uq_payments_payment_key (payment_key),

    CONSTRAINT fk_payments_participation
        FOREIGN KEY (participation_id)
            REFERENCES participations (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_parent
        FOREIGN KEY (parent_payment_id)
            REFERENCES payments (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deliveries
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id       BIGINT       NOT NULL COMMENT '참여 ID',
    shipping_method        VARCHAR(20)  NOT NULL COMMENT '배송 방식 스냅샷',
    store_name             VARCHAR(100) NOT NULL COMMENT '편의점 지점명 스냅샷',
    receiver_nickname      VARCHAR(20)  NOT NULL COMMENT '닉네임 스냅샷',
    receiver_phone_number  VARCHAR(15)  NOT NULL COMMENT '연락처 스냅샷',
    tracking_number        VARCHAR(100) NULL COMMENT '운송장 번호',
    tracking_registered_at DATETIME     NULL COMMENT '운송장 등록 시각',
    delivered_at           DATETIME     NULL COMMENT '배송 완료 시각',
    received_at            DATETIME     NULL COMMENT '사용자 수령 완료 시각',
    status                 VARCHAR(20)  NOT NULL DEFAULT 'SNAPSHOTTED' COMMENT 'SNAPSHOTTED | SHIPPING | DELIVERED | RECEIVED',
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_deliveries_participation_id (participation_id),
    INDEX idx_deliveries_status (status),

    CONSTRAINT fk_deliveries_participation
        FOREIGN KEY (participation_id)
            REFERENCES participations (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settlements
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    buncheol_id   BIGINT      NOT NULL COMMENT '분철 ID',
    status        VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED | SETTLING | SETTLED',
    gross_amount  BIGINT      NOT NULL COMMENT '총 결제 금액',
    refund_amount BIGINT      NOT NULL COMMENT '총 환불 금액',
    net_amount    BIGINT      NOT NULL COMMENT '정산 금액(총 결제 - 총 환불)',
    scheduled_at  DATETIME    NULL COMMENT '정산 예정 시각',
    settled_at    DATETIME    NULL COMMENT '정산 완료 시각',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_settlements_buncheol_id (buncheol_id),
    INDEX idx_settlements_status (status),

    CONSTRAINT fk_settlements_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- buncheol_bookmarks 테이블 생성 (찜한 분철)
CREATE TABLE IF NOT EXISTS buncheol_bookmarks
(
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL COMMENT '찜한 유저',
    buncheol_id BIGINT   NOT NULL COMMENT '찜한 분철',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheol_bookmarks_user_id (user_id),
    UNIQUE INDEX uq_buncheol_bookmarks_user_buncheol (user_id, buncheol_id),

    CONSTRAINT fk_buncheol_bookmarks_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_bookmarks_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- user_favorite_groups 테이블 생성 (유저 최애 그룹)
CREATE TABLE IF NOT EXISTS user_favorite_groups
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL COMMENT '유저',
    group_id   BIGINT   NOT NULL COMMENT '최애 그룹',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_user_favorite_groups_user_id (user_id),
    UNIQUE INDEX uq_user_favorite_groups_user_group (user_id, group_id),

    CONSTRAINT fk_user_favorite_groups_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_favorite_groups_group
        FOREIGN KEY (group_id)
            REFERENCES `groups` (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

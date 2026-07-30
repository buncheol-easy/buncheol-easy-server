-- Test H2 Database용 테이블 생성
-- FK 역순으로 DROP (자식 → 부모 순서)
DROP TABLE IF EXISTS inbox_messages;
DROP TABLE IF EXISTS admins;
DROP TABLE IF EXISTS deliveries;
DROP TABLE IF EXISTS participations;
DROP TABLE IF EXISTS buncheol_images;
DROP TABLE IF EXISTS buncheol_members;
DROP TABLE IF EXISTS buncheol_bookmarks;
DROP TABLE IF EXISTS buncheols;
DROP TABLE IF EXISTS user_recent_searches;
DROP TABLE IF EXISTS user_favorite_groups;
DROP TABLE IF EXISTS group_members;
DROP TABLE IF EXISTS `groups`;
DROP TABLE IF EXISTS shipping_addresses;
DROP TABLE IF EXISTS user_service_terms;
DROP TABLE IF EXISTS users;

CREATE TABLE users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL,
    provider_id         VARCHAR(100) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    nickname            VARCHAR(20)  NOT NULL,
    name                VARCHAR(30)  NULL,
    phone_number        VARCHAR(15)  NULL,
    settlement_bank     VARCHAR(50)  NULL,
    settlement_account  VARCHAR(50)  NULL,
    settlement_holder   VARCHAR(50)  NULL,
    profile_completed   BOOLEAN      NOT NULL DEFAULT FALSE,
    can_host            BOOLEAN      NOT NULL DEFAULT FALSE,
    marketing_agreed_at TIMESTAMP    NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP    NULL,

    -- 유니크 제약조건을 위한 가상 컬럼 (deleted_at이 NULL이면 값을 갖고, 유저가 탈퇴하면 NULL이 된다)
    _active_provider    VARCHAR(20) AS (CASE WHEN deleted_at IS NULL THEN provider END),
    _active_provider_id VARCHAR(100) AS (CASE WHEN deleted_at IS NULL THEN provider_id END),
    _active_nickname    VARCHAR(20) AS (CASE WHEN deleted_at IS NULL THEN nickname END),

    PRIMARY KEY (id)
);

-- soft delete 패턴의 유니크 제약 (탈퇴 후 재가입 허용 여부를 DB 레벨에서 보장)
CREATE UNIQUE INDEX uq_users_active_social_account ON users (_active_provider, _active_provider_id);
CREATE UNIQUE INDEX uq_users_active_nickname ON users (_active_nickname);

-- Test H2 Database용 user_service_terms 테이블 생성 (카카오 간편가입 약관 동의 내역)
CREATE TABLE user_service_terms
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    tag        VARCHAR(100) NOT NULL,
    agreed     BOOLEAN      NOT NULL,
    agreed_at  TIMESTAMP    NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_user_service_terms_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_user_service_terms_user_tag ON user_service_terms (user_id, tag);

-- Test H2 Database용 shipping_addresses 테이블 생성
CREATE TABLE shipping_addresses
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    shipping_method VARCHAR(20)  NOT NULL,
    store_name      VARCHAR(100) NOT NULL,
    alias           VARCHAR(10)  NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_shipping_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 중복 배송지 제약 (동일 유저의 동일 배송방법+지점명 등록 불가)
CREATE UNIQUE INDEX uq_shipping_addresses_user_method_store ON shipping_addresses (user_id, shipping_method, store_name);

-- Test H2 Database용 buncheol 관련 테이블 생성
CREATE TABLE `groups`
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    image      VARCHAR(500) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

CREATE INDEX idx_groups_name ON `groups` (name);

CREATE TABLE group_members
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    image      VARCHAR(500) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES `groups` (id) ON DELETE CASCADE
);

CREATE INDEX idx_group_members_group_id ON group_members (group_id);
CREATE INDEX idx_group_members_name ON group_members (name);

CREATE TABLE buncheols
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    host_id           BIGINT       NOT NULL,
    group_id          BIGINT       NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       TEXT         NULL,
    purchase_site     VARCHAR(200) NOT NULL,
    deadline          TIMESTAMP    NOT NULL,
    min_headcount     INT          NOT NULL,
    gs25_shipping_fee INT          NULL,
    cu_shipping_fee   INT          NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING',
    finalized_at      TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheols_host FOREIGN KEY (host_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheols_group FOREIGN KEY (group_id) REFERENCES `groups` (id)
);

CREATE INDEX idx_buncheols_group_id ON buncheols (group_id);
CREATE INDEX idx_buncheols_title ON buncheols (title);
CREATE INDEX idx_buncheols_host_created ON buncheols (host_id, created_at DESC);
CREATE INDEX idx_buncheols_status_created ON buncheols (status, created_at DESC, id DESC);
CREATE INDEX idx_buncheols_group_created ON buncheols (group_id, created_at DESC, id DESC);
CREATE INDEX idx_buncheols_status_deadline ON buncheols (status, deadline);

CREATE TABLE buncheol_members
(
    id          BIGINT    NOT NULL AUTO_INCREMENT,
    buncheol_id BIGINT    NOT NULL,
    member_id   BIGINT    NOT NULL,
    price       BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_members_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_members_member FOREIGN KEY (member_id) REFERENCES group_members (id)
);

CREATE INDEX idx_buncheol_members_buncheol_id ON buncheol_members (buncheol_id);
CREATE UNIQUE INDEX uq_buncheol_members_buncheol_member ON buncheol_members (buncheol_id, member_id);
CREATE INDEX idx_buncheol_members_member ON buncheol_members (member_id, buncheol_id);

CREATE TABLE buncheol_images
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id  BIGINT       NOT NULL,
    image_url    VARCHAR(500) NOT NULL,
    is_thumbnail BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_images_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE
);

CREATE INDEX idx_buncheol_images_buncheol_id ON buncheol_images (buncheol_id);

CREATE TABLE participations
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id         BIGINT       NOT NULL,
    buncheol_member_id  BIGINT       NOT NULL,
    participant_id      BIGINT       NOT NULL,
    shipping_address_id BIGINT       NULL,
    amount              BIGINT       NOT NULL,
    shipping_fee        BIGINT       NOT NULL DEFAULT 0,
    refund_bank         VARCHAR(50)  NOT NULL,
    refund_account      VARCHAR(50)  NOT NULL,
    refund_holder       VARCHAR(50)  NOT NULL,
    due_at              TIMESTAMP    NOT NULL,
    confirmed_at        TIMESTAMP    NULL,
    cancelled_at        TIMESTAMP    NULL,
    cancel_reason       VARCHAR(30)  NULL,
    status              VARCHAR(30)  NOT NULL,
    -- 오픈 이벤트 배송비 환급 (schema.sql participations 와 동일 구성)
    payback_status        VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    payback_tweet_url     VARCHAR(255) NULL,
    payback_requested_at  TIMESTAMP    NULL,
    payback_completed_at  TIMESTAMP    NULL,
    payback_reject_reason VARCHAR(200) NULL,
    payback_amount        BIGINT       NULL,
    -- 활성 상태일 때만 멤버 슬롯 id 값을 갖는 가상 컬럼 (선착순 유니크용). users 테이블과 동일하게 H2 computed column 사용.
    active_member_id    BIGINT AS (CASE WHEN status IN ('AWAITING_PAYMENT', 'CONFIRMED') THEN buncheol_member_id END),
    -- 활성 상태일 때만 참여자 id 값을 갖는 가상 컬럼 (분철당 중복 참여 방지 유니크용).
    active_participant_id BIGINT AS (CASE WHEN status IN ('AWAITING_PAYMENT', 'CONFIRMED') THEN participant_id END),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_participations_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_participations_buncheol_member FOREIGN KEY (buncheol_member_id) REFERENCES buncheol_members (id),
    CONSTRAINT fk_participations_user FOREIGN KEY (participant_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_participations_shipping_address FOREIGN KEY (shipping_address_id) REFERENCES shipping_addresses (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_participations_active_member ON participations (active_member_id);
CREATE UNIQUE INDEX uq_participations_active_participant ON participations (buncheol_id, active_participant_id);
CREATE INDEX idx_participations_buncheol_status ON participations (buncheol_id, status);
CREATE INDEX idx_participations_status_due ON participations (status, due_at);
CREATE INDEX idx_participations_participant_created ON participations (participant_id, created_at DESC);
CREATE INDEX idx_participations_created ON participations (created_at DESC, id DESC);
CREATE UNIQUE INDEX uq_participations_payback_tweet_url ON participations (payback_tweet_url);
CREATE INDEX idx_participations_payback_requested ON participations (payback_status, payback_requested_at DESC, id DESC);

-- Test H2 Database용 admins 테이블 생성 (관리자 계정 — 독립 ID/PW 계정)
CREATE TABLE admins
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50)  NOT NULL,
    password   VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_admins_login_id ON admins (login_id);

CREATE TABLE deliveries
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id       BIGINT       NOT NULL,
    shipping_method        VARCHAR(20)  NOT NULL,
    store_name             VARCHAR(100) NOT NULL,
    receiver_nickname      VARCHAR(20)  NOT NULL,
    receiver_phone_number  VARCHAR(15)  NOT NULL,
    tracking_number        VARCHAR(100) NULL,
    tracking_registered_at TIMESTAMP    NULL,
    delivered_at           TIMESTAMP    NULL,
    received_at            TIMESTAMP    NULL,
    pickup_reminder_sent_at TIMESTAMP NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'SNAPSHOTTED',
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_deliveries_participation FOREIGN KEY (participation_id) REFERENCES participations (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_deliveries_participation_id ON deliveries (participation_id);
CREATE INDEX idx_deliveries_status ON deliveries (status);
CREATE INDEX idx_deliveries_tracking ON deliveries (tracking_number, shipping_method);

CREATE TABLE buncheol_bookmarks
(
    id          BIGINT    NOT NULL AUTO_INCREMENT,
    user_id     BIGINT    NOT NULL,
    buncheol_id BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_bookmarks_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE
);

CREATE INDEX idx_buncheol_bookmarks_user_id ON buncheol_bookmarks (user_id);
CREATE UNIQUE INDEX uq_buncheol_bookmarks_user_buncheol ON buncheol_bookmarks (user_id, buncheol_id);

CREATE TABLE user_favorite_groups
(
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    group_id   BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_user_favorite_groups_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_favorite_groups_group FOREIGN KEY (group_id) REFERENCES `groups` (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_favorite_groups_user_id ON user_favorite_groups (user_id);
CREATE UNIQUE INDEX uq_user_favorite_groups_user_group ON user_favorite_groups (user_id, group_id);

CREATE TABLE user_recent_searches
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    keyword    VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_user_recent_searches_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_recent_searches_user_created ON user_recent_searches (user_id, created_at DESC, id DESC);

CREATE TABLE inbox_messages
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    type         VARCHAR(20)  NOT NULL,
    recipient_id BIGINT       NULL,
    title        VARCHAR(200) NOT NULL,
    reference    VARCHAR(200) NULL,
    description  TEXT         NOT NULL,
    pinned           BOOLEAN      NOT NULL DEFAULT FALSE,
    link_path        VARCHAR(500) NULL,
    image_url        VARCHAR(500) NULL,
    banner_title     VARCHAR(200) NULL,
    banner_image_url VARCHAR(500) NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_inbox_messages_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_inbox_recipient_created ON inbox_messages (recipient_id, created_at DESC, id DESC);
CREATE INDEX idx_inbox_type_pinned_created ON inbox_messages (type, pinned, created_at DESC, id DESC);
CREATE INDEX idx_inbox_banner ON inbox_messages (banner_image_url);

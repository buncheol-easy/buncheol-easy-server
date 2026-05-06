-- Test H2 Database용 테이블 생성
-- FK 역순으로 DROP (자식 → 부모 순서)
DROP TABLE IF EXISTS deliveries;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS participations;
DROP TABLE IF EXISTS buncheol_images;
DROP TABLE IF EXISTS buncheol_members;
DROP TABLE IF EXISTS buncheols;
DROP TABLE IF EXISTS group_members;
DROP TABLE IF EXISTS `groups`;
DROP TABLE IF EXISTS shipping_addresses;
DROP TABLE IF EXISTS users;

CREATE TABLE users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL,
    provider_id         VARCHAR(100) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    nickname            VARCHAR(20)  NOT NULL,
    phone_number        VARCHAR(15)  NULL,
    settlement_bank     VARCHAR(50)  NULL,
    settlement_account  VARCHAR(50)  NULL,
    settlement_holder   VARCHAR(50)  NULL,
    profile_completed   BOOLEAN      NOT NULL DEFAULT FALSE,
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

-- Test H2 Database용 shipping_addresses 테이블 생성
CREATE TABLE shipping_addresses
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    shipping_method VARCHAR(20)  NOT NULL,
    store_name      VARCHAR(100) NOT NULL,
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
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    host_id                BIGINT       NOT NULL,
    group_id               BIGINT       NOT NULL,
    title                  VARCHAR(200) NOT NULL,
    description            TEXT         NULL,
    purchase_site          VARCHAR(200) NOT NULL,
    deadline               TIMESTAMP    NOT NULL,
    shipping_deadline_days TIMESTAMP    NULL,
    gs25_shipping_fee      INT          NULL,
    cu_shipping_fee        INT          NULL,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING',
    closed_at              TIMESTAMP    NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheols_host FOREIGN KEY (host_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheols_group FOREIGN KEY (group_id) REFERENCES `groups` (id)
);

CREATE INDEX idx_buncheols_group_id ON buncheols (group_id);
CREATE INDEX idx_buncheols_title ON buncheols (title);

CREATE TABLE buncheol_members
(
    id            BIGINT    NOT NULL AUTO_INCREMENT,
    buncheol_id   BIGINT    NOT NULL,
    member_id     BIGINT    NOT NULL,
    bid_min_price BIGINT    NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_members_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_members_member FOREIGN KEY (member_id) REFERENCES group_members (id)
);

CREATE INDEX idx_buncheol_members_buncheol_id ON buncheol_members (buncheol_id);
CREATE UNIQUE INDEX uq_buncheol_members_buncheol_member ON buncheol_members (buncheol_id, member_id);

CREATE TABLE buncheol_images
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id BIGINT       NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_images_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE
);

CREATE INDEX idx_buncheol_images_buncheol_id ON buncheol_images (buncheol_id);

CREATE TABLE participations
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id           BIGINT       NOT NULL,
    buncheol_member_id    BIGINT       NOT NULL,
    participant_id        BIGINT       NOT NULL,
    shipping_address_id   BIGINT       NOT NULL,
    bid_amount            BIGINT       NOT NULL,
    due_at                TIMESTAMP    NULL,
    closed_rank           INT          NULL,
    fail_reason           VARCHAR(100) NULL,
    finalized_at          TIMESTAMP    NULL,
    status                VARCHAR(30)  NOT NULL,
    -- H2 generated column 안정성 이슈로 테스트 스키마에서는 일반 컬럼으로 유지
    confirmed_member_id   BIGINT       NULL,
    active_participant_id BIGINT       NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_participations_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_participations_buncheol_member FOREIGN KEY (buncheol_member_id) REFERENCES buncheol_members (id),
    CONSTRAINT fk_participations_user FOREIGN KEY (participant_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_participations_shipping_address FOREIGN KEY (shipping_address_id) REFERENCES shipping_addresses (id) ON DELETE CASCADE
);

CREATE INDEX idx_participations_buncheol_id ON participations (buncheol_id);
CREATE UNIQUE INDEX uq_participations_confirmed_member ON participations (confirmed_member_id);
CREATE UNIQUE INDEX uq_participations_active_member_participant ON participations (buncheol_member_id, active_participant_id);
CREATE INDEX idx_participations_member_status ON participations (buncheol_member_id, status);

CREATE TABLE payments
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id  BIGINT       NOT NULL,
    tx_type           VARCHAR(20)  NOT NULL,
    order_id          VARCHAR(100) NOT NULL,
    payment_key       VARCHAR(200) NULL,
    parent_payment_id BIGINT       NULL,
    amount            BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    reason            VARCHAR(255) NULL,
    requested_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at       TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uq_payments_order_id UNIQUE (order_id),
    CONSTRAINT uq_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_payments_participation FOREIGN KEY (participation_id) REFERENCES participations (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_parent FOREIGN KEY (parent_payment_id) REFERENCES payments (id) ON DELETE SET NULL
);

CREATE INDEX idx_payments_participation_id ON payments (participation_id);
CREATE INDEX idx_payments_tx_type_status ON payments (tx_type, status);

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
    status                 VARCHAR(20)  NOT NULL DEFAULT 'SNAPSHOTTED',
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_deliveries_participation FOREIGN KEY (participation_id) REFERENCES participations (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_deliveries_participation_id ON deliveries (participation_id);
CREATE INDEX idx_deliveries_status ON deliveries (status);

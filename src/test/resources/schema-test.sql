-- Test H2 Database용 테이블 생성
-- FK 역순으로 DROP (자식 → 부모 순서)
DROP TABLE IF EXISTS buncheol_images;
DROP TABLE IF EXISTS buncheol_members;
DROP TABLE IF EXISTS buncheols;
DROP TABLE IF EXISTS group_members;
DROP TABLE IF EXISTS `groups`;
DROP TABLE IF EXISTS shipping_addresses;
DROP TABLE IF EXISTS users;

CREATE TABLE users
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    provider           VARCHAR(20)  NOT NULL,
    provider_id        VARCHAR(100) NOT NULL,
    email              VARCHAR(320) NOT NULL,
    nickname           VARCHAR(20)  NOT NULL,
    phone_number       VARCHAR(15)  NULL,
    profile_completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP    NULL,

    -- 유니크 제약조건을 위한 가상 컬럼 (deleted_at이 NULL이면 값을 갖고, 유저가 탈퇴하면 NULL이 된다)
    active_provider    VARCHAR(20) AS (CASE WHEN deleted_at IS NULL THEN provider END),
    active_provider_id VARCHAR(100) AS (CASE WHEN deleted_at IS NULL THEN provider_id END),
    active_nickname    VARCHAR(20) AS (CASE WHEN deleted_at IS NULL THEN nickname END),
    active_phone       VARCHAR(15) AS (CASE WHEN deleted_at IS NULL THEN phone_number END),

    PRIMARY KEY (id)
);

-- soft delete 패턴의 유니크 제약 (탈퇴 후 재가입 허용 여부를 DB 레벨에서 보장)
CREATE UNIQUE INDEX uq_users_active_social_account ON users (active_provider, active_provider_id);
CREATE UNIQUE INDEX uq_users_active_nickname ON users (active_nickname);
CREATE UNIQUE INDEX uq_users_active_phone ON users (active_phone);

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
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

CREATE TABLE group_members
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES `groups` (id) ON DELETE CASCADE
);

CREATE TABLE buncheols
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    host_id                BIGINT       NOT NULL,
    group_id               BIGINT       NULL,
    group_name             VARCHAR(100) NOT NULL,
    title                  VARCHAR(200) NOT NULL,
    description            VARCHAR(300) NULL,
    goods_name             VARCHAR(200) NOT NULL,
    store_name             VARCHAR(200) NOT NULL,
    original_price         BIGINT       NOT NULL,
    deadline               TIMESTAMP    NOT NULL,
    shipping_deadline_days INT          NOT NULL,
    gs25_shipping_fee      INT          NULL,
    cu_shipping_fee        INT          NULL,
    settlement_bank        VARCHAR(50)  NOT NULL,
    settlement_account     VARCHAR(50)  NOT NULL,
    settlement_holder      VARCHAR(50)  NOT NULL,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING',
    closed_at              TIMESTAMP    NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheols_host  FOREIGN KEY (host_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheols_group FOREIGN KEY (group_id) REFERENCES `groups` (id) ON DELETE SET NULL
);

CREATE TABLE buncheol_members
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id   BIGINT       NOT NULL,
    member_id     BIGINT       NULL,
    member_name   VARCHAR(100) NOT NULL,
    instant_price BIGINT       NOT NULL,
    bid_allowed   BOOLEAN      NOT NULL DEFAULT FALSE,
    bid_min_price BIGINT       NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_members_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_members_member   FOREIGN KEY (member_id)   REFERENCES group_members (id) ON DELETE SET NULL
);

CREATE TABLE buncheol_images
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id BIGINT       NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_buncheol_images_buncheol FOREIGN KEY (buncheol_id) REFERENCES buncheols (id) ON DELETE CASCADE
);

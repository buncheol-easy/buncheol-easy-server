-- Test H2 Database용 테이블 생성
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

-- Test H2 Database용 users 테이블 생성
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

-- 가상 컬럼에 유니크 인덱스
CREATE UNIQUE INDEX uq_users_active_social_account ON users (active_provider, active_provider_id);
CREATE UNIQUE INDEX uq_users_active_nickname ON users (active_nickname);
CREATE UNIQUE INDEX uq_users_active_phone ON users (active_phone);

-- 일반 인덱스
CREATE INDEX idx_users_provider_id ON users (provider_id);
CREATE INDEX idx_users_nickname ON users (nickname);

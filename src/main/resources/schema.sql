-- users 테이블 생성
CREATE TABLE IF NOT EXISTS users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL COMMENT '소셜 로그인 제공자 (KAKAO, GOOGLE, APPLE …)',
    provider_id         VARCHAR(100) NOT NULL COMMENT '소셜 제공자 고유 ID',
    email               VARCHAR(320) NOT NULL COMMENT '소셜 계정 이메일',
    nickname            VARCHAR(20)  NOT NULL COMMENT '닉네임',
    phone_number        VARCHAR(15)  NULL COMMENT '연락처',
    profile_completed   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '프로필 설정 완료 여부',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          DATETIME     NULL COMMENT '회원탈퇴 soft delete',

    -- 유니크 제약조건을 위한 가상 컬럼 (deleted_at이 NULL이면 값을 갖고, 유저가 탈퇴하면 NULL이 된다)
    _active_provider    VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, provider, NULL)) VIRTUAL,
    _active_provider_id VARCHAR(100) GENERATED ALWAYS AS (IF(deleted_at IS NULL, provider_id, NULL)) VIRTUAL,
    _active_nickname    VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, nickname, NULL)) VIRTUAL,
    _active_phone       VARCHAR(15) GENERATED ALWAYS AS (IF(deleted_at IS NULL, phone_number, NULL)) VIRTUAL,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_users_active_social_account (_active_provider, _active_provider_id),
    UNIQUE INDEX uq_users_active_nickname (_active_nickname),
    UNIQUE INDEX uq_users_active_phone (_active_phone),

    INDEX idx_users_provider_id (provider_id),
    INDEX idx_users_nickname (nickname)
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
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_shipping_address_user_id (user_id),
    UNIQUE INDEX idx_shipping_address_user_method (user_id, shipping_method),

    CONSTRAINT fk_shipping_address_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

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
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    host_id                BIGINT       NOT NULL COMMENT '개최자',
    group_id               BIGINT       NULL COMMENT '대상 그룹 (NULL이면 커스텀 그룹)',
    group_name             VARCHAR(100) NOT NULL COMMENT '그룹명 (반정규화/커스텀)',
    title                  VARCHAR(200) NOT NULL COMMENT '분철 제목',
    description            TEXT         NULL COMMENT '분철 설명',
    goods_name             VARCHAR(200) NOT NULL COMMENT '굿즈명',
    store_name             VARCHAR(200) NOT NULL COMMENT '구매처',
    original_price         INT          NOT NULL COMMENT '원가(굿즈 1개당)',
    deadline               DATETIME     NOT NULL COMMENT '분철 마감일',
    shipping_deadline_days INT          NOT NULL COMMENT '발송 마감 일수(수령 후 n일)',
    gs25_shipping_fee      INT          NULL COMMENT 'GS25반값택배 배송비',
    cu_shipping_fee        INT          NULL COMMENT 'CU반값택배 배송비',
    settlement_bank        VARCHAR(50)  NOT NULL COMMENT '정산 은행',
    settlement_account     VARCHAR(50)  NOT NULL COMMENT '정산 계좌번호',
    settlement_holder      VARCHAR(50)  NOT NULL COMMENT '정산 예금주',
    status                 VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING | CLOSED | GOODS_ORDERED | SELLER_SHIPPING | HOST_SHIPPING | ALL_RECEIVED | SETTLING | SETTLED | FINISHED',
    closed_at              DATETIME     NULL COMMENT '마감 일시',
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheols_group_id (group_id),
    INDEX idx_buncheols_goods_name (goods_name),
    INDEX idx_buncheols_title (title),

    CONSTRAINT fk_buncheols_host
        FOREIGN KEY (host_id)
            REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheols_group
        FOREIGN KEY (group_id)
            REFERENCES `groups` (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buncheol_members
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id   BIGINT       NOT NULL,
    member_id     BIGINT       NULL COMMENT '대상 멤버 (NULL이면 커스텀 멤버)',
    member_name   VARCHAR(100) NOT NULL COMMENT '멤버명 (반정규화/커스텀)',
    instant_price INT          NOT NULL COMMENT '즉시 구매 가격',
    bid_allowed   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '제시 가능 여부',
    bid_min_price INT          NULL COMMENT '제시 최소 금액',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheol_members_buncheol_id (buncheol_id),

    CONSTRAINT fk_buncheol_members_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_buncheol_members_member
        FOREIGN KEY (member_id)
            REFERENCES group_members (id) ON DELETE SET NULL
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

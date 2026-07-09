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

-- admins 테이블 생성 (관리자 계정 — 서비스 유저와 무관한 독립 ID/PW 계정)
-- 계정 생성은 배포 환경변수 부트스트랩(AdminAccountInitializer) 또는 운영자의 직접 INSERT(BCrypt 해시)로 한다.
-- 관리자 access token 은 role claim(ADMIN)으로 유저 토큰과 구분되며, 유저 API 는 hasRole(USER) 라 관리자
-- 토큰으로 접근할 수 없다 (admin.id 와 users.id 가 겹쳐도 위장 불가).
CREATE TABLE IF NOT EXISTS admins
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50)  NOT NULL COMMENT '관리자 로그인 ID',
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_admins_login_id (login_id)
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
    min_headcount     INT          NOT NULL COMMENT '분철 진행 최소 인원',
    gs25_shipping_fee INT          NULL COMMENT 'GS25반값택배 배송비',
    cu_shipping_fee   INT          NULL COMMENT 'CU반값택배 배송비',
    status            VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING | CONFIRMED | CANCELLED(인원미달 자동취소) | HOST_CANCELLED(개최자 취소, 목록·상세 비노출)',
    finalized_at      DATETIME     NULL COMMENT '마감 판정(진행확정/취소) 시각',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheols_group_id (group_id),
    INDEX idx_buncheols_title (title),
    INDEX idx_buncheols_host_created (host_id, created_at DESC),
    -- 공개 목록 '모집중' 그룹 조회 (status='RECRUITING', createdAt DESC, id DESC) 의 커서 페이지네이션용
    INDEX idx_buncheols_status_created (status, created_at DESC, id DESC),
    -- groupId 필터 + 커서 조합용 (idx_buncheols_group_id 만으로는 정렬을 인덱스로 커버 불가)
    INDEX idx_buncheols_group_created (group_id, created_at DESC, id DESC),
    -- 두 용도 겸용: (1) 자동 마감 스케줄러 폴링 (status='RECRUITING' AND deadline <= now, 앞 두 컬럼 prefix),
    -- (2) 공개 목록 '마감' 그룹 조회 (status='CONFIRMED', deadline DESC, id DESC) 의 커서 페이지네이션 — 역방향 인덱스 스캔으로 정렬 커버.
    -- 단 groupId 필터 + '마감' 그룹 조합은 (group_id, deadline, id) 인덱스가 없어 정렬이 filesort 로 떨어진다 (그룹별 마감분이 적어 현재는 수용,
    -- 특정 그룹 마감분이 많아지면 (group_id, deadline DESC, id DESC) 추가 검토).
    INDEX idx_buncheols_status_deadline (status, deadline, id),

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
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    buncheol_id BIGINT   NOT NULL,
    member_id   BIGINT   NOT NULL COMMENT '대상 멤버',
    price       BIGINT   NOT NULL COMMENT '멤버 1명당 고정 금액 (100원 단위)',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id         BIGINT       NOT NULL,
    buncheol_member_id  BIGINT       NOT NULL COMMENT '참여한 멤버 슬롯',
    participant_id      BIGINT       NOT NULL COMMENT '참여자',
    shipping_address_id BIGINT       NULL COMMENT '선택한 배송지 (참조 배송지 삭제 시 NULL — 종료된 참여 한정)',
    amount              BIGINT       NOT NULL COMMENT '멤버 금액 (굿즈 가격, 배송비 제외)',
    shipping_fee        BIGINT       NOT NULL DEFAULT 0 COMMENT '배송비 (묶음당 1회만 부과; 묶음 첫 슬롯만 >0). 입금 총액 = amount + shipping_fee',
    refund_bank         VARCHAR(50)  NOT NULL COMMENT '환불 은행',
    refund_account      VARCHAR(50)  NOT NULL COMMENT '환불 계좌번호',
    refund_holder       VARCHAR(50)  NOT NULL COMMENT '환불 예금주',
    due_at              DATETIME     NOT NULL COMMENT '입금 만료 시각 = min(점유+30분, deadline)',
    confirmed_at        DATETIME     NULL COMMENT '개최자 입금확인 시각',
    cancelled_at        DATETIME     NULL COMMENT '참여 취소 시각',
    cancel_reason       VARCHAR(30)  NULL COMMENT 'PAYMENT_TIMEOUT | BUNCHEOL_CANCELLED',
    status              VARCHAR(30)  NOT NULL COMMENT 'AWAITING_PAYMENT | CONFIRMED | CANCELLED',
    -- 멤버 슬롯당 활성 참여 1건(선착순) 보장용 가상 컬럼: 활성 상태일 때만 멤버 슬롯 id 값을 갖고, 취소/만료되면 NULL 이 되어 슬롯이 다시 열린다.
    active_member_id    BIGINT GENERATED ALWAYS AS (
                            IF(status IN ('AWAITING_PAYMENT', 'CONFIRMED'), buncheol_member_id, NULL)
                            ) STORED COMMENT '활성 상태일 때만 buncheol_member_id 값 (선착순 유니크용)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 멤버 슬롯당 활성 참여 1건 (선착순). 동시 참여 시 두 번째 INSERT 가 이 제약에 막혀 DuplicateKey 로 떨어진다.
    UNIQUE INDEX uq_participations_active_member (active_member_id),
    -- 분철별 상태 집계(확정 인원 카운트)·호스트 참여 목록 조회용
    INDEX idx_participations_buncheol_status (buncheol_id, status),
    -- 입금 만료 스케줄러 폴링(status='AWAITING_PAYMENT' AND due_at<=now)용
    INDEX idx_participations_status_due (status, due_at),
    -- 내 참여 목록(참여자별 최신순)용
    INDEX idx_participations_participant_created (participant_id, created_at DESC),
    -- 관리자 결제 목록(전체 참여 최신순) 커서 페이지네이션용. 기존 배포 DB 에는 수동 ALTER 필요
    -- (CREATE TABLE IF NOT EXISTS 는 기존 테이블에 인덱스를 추가하지 않는다).
    INDEX idx_participations_created (created_at DESC, id DESC),

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
            REFERENCES shipping_addresses (id) ON DELETE SET NULL
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

-- user_recent_searches 테이블 생성 (검색창 최근 검색어 — 사용자가 친 단어를 단일 컬럼에 보관)
-- 프론트가 그룹·멤버 name → id 변환을 책임지므로, 서버는 검색 요청의 (keyword|groupId|memberId)
-- 중 무엇이 와도 최종적으로 사용자가 입력한 텍스트 1개로 normalize 해 저장한다.
CREATE TABLE IF NOT EXISTS user_recent_searches
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL COMMENT '검색 사용자',
    keyword    VARCHAR(100) NOT NULL COMMENT '사용자가 검색창에 친 텍스트 (group.name / member.name normalize 포함)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 사용자별 최신순 조회 + 7개 초과 정리(offset) 쿼리 양쪽 모두 활용
    INDEX idx_user_recent_searches_user_created (user_id, created_at DESC, id DESC),

    CONSTRAINT fk_user_recent_searches_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- inbox_messages 테이블 생성 (수신함 - 공지/알림 단일 관리)
-- 공지(NOTICE)는 전체 대상이라 recipient_id NULL, 알림(NOTIFICATION)은 수신자별 1:1 생성.
CREATE TABLE IF NOT EXISTS inbox_messages
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    type             VARCHAR(20)  NOT NULL COMMENT 'NOTICE | NOTIFICATION',
    recipient_id     BIGINT       NULL COMMENT '알림 수신자 (공지는 NULL)',
    title            VARCHAR(200) NOT NULL COMMENT '제목',
    reference        VARCHAR(200) NULL COMMENT '보조 텍스트(참고)',
    description      TEXT         NOT NULL COMMENT '설명(본문)',
    pinned           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '상단 고정 여부 (공지만 사용)',
    link_path        VARCHAR(500) NULL COMMENT '연관 화면 in-app 경로',
    image_url        VARCHAR(500) NULL COMMENT '공지 본문 이미지 URL (공지만 사용)',
    banner_title     VARCHAR(200) NULL COMMENT '홈 배너 제목 (공지만 사용)',
    banner_image_url VARCHAR(500) NULL COMMENT '홈 배너 이미지 URL (공지만 사용)',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 본인 알림 피드(recipient_id = userId, pinned = false) 커서 페이지네이션용
    INDEX idx_inbox_recipient_created (recipient_id, created_at DESC, id DESC),
    -- 공지 피드(pinned = false) 및 상단 고정 공지(pinned = true) 조회용
    INDEX idx_inbox_type_pinned_created (type, pinned, created_at DESC, id DESC),
    -- 홈 배너 조회용 (배너 등록 공지 = banner_image_url IS NOT NULL 시크)
    INDEX idx_inbox_banner (banner_image_url),

    CONSTRAINT fk_inbox_messages_recipient
        FOREIGN KEY (recipient_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

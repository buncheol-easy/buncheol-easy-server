-- ⚠️ 스키마 변경 운영 규칙 — expand-contract (루트 docs/39):
-- 블루-그린 배포는 구·신 버전이 같은 DB 를 수십 초 동시에 본다. 컬럼 삭제·rename·NOT NULL 추가
-- 같은 파괴적 변경(contract)을 코드 배포와 한 릴리스에 묶으면 전환 창에서 구버전이 죽는다.
-- 절차: ① expand(컬럼/테이블 추가)는 "배포 전에 수동 ALTER 로" 먼저 반영한다 — 이 파일은
--   전부 CREATE TABLE IF NOT EXISTS 라 기존 운영 DB 의 테이블에 컬럼을 추가해 주지 않는다
--   (아래 곳곳의 "기존 배포 DB 에는 수동 ALTER 필요" 주석이 그 얘기다). 수동 ALTER 없이
--   배포하면 신버전이 헬스는 통과하고(그 컬럼을 안 건드리므로) 전환 직후부터 500 이 난다.
-- ② contract(제거·rename·NOT NULL 강제)는 그걸 안 쓰는 코드가 완전히 내려간 다음 릴리스에서.
-- ③ rename 은 "새 컬럼 추가 → 양쪽 쓰기 → 구 컬럼 제거" 3단계로 쪼갠다.

-- users 테이블 생성
CREATE TABLE IF NOT EXISTS users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL COMMENT '소셜 로그인 제공자 (KAKAO, GOOGLE, APPLE …)',
    provider_id         VARCHAR(100) NOT NULL COMMENT '소셜 제공자 고유 ID',
    email               VARCHAR(320) NOT NULL COMMENT '소셜 계정 이메일',
    nickname            VARCHAR(20)  NOT NULL COMMENT '닉네임',
    -- NULL 허용: 기존 회원은 값이 없고 마이페이지에서 수시 입력한다 (신규 가입은 FE 에서 필수 강제).
    -- 기존 배포 DB 에는 수동 ALTER 필요.
    name                VARCHAR(30)  NULL COMMENT '실명 (입금 대조·배송 연락 참조)',
    phone_number        VARCHAR(15)  NULL COMMENT '연락처',
    settlement_bank     VARCHAR(50)  NULL COMMENT '정산 은행',
    settlement_account  VARCHAR(50)  NULL COMMENT '정산 계좌번호',
    settlement_holder   VARCHAR(50)  NULL COMMENT '정산 예금주',
    profile_completed   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '프로필 설정 완료 여부',
    -- 개최 오픈 전 운영 지정 계정만 true. 부여는 DB 직접 UPDATE. 기존 배포 DB 에는 수동 ALTER 필요
    -- (CREATE TABLE IF NOT EXISTS 는 기존 테이블에 컬럼을 추가하지 않는다).
    can_host            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '분철 개최 허용 여부',
    -- NULL = 미동의/철회. 광고성 정보는 동의 일시 기록·2년 주기 재확인 의무가 있어 boolean 대신 일시로 저장.
    -- 기존 배포 DB 에는 수동 ALTER 필요.
    marketing_agreed_at DATETIME     NULL COMMENT '마케팅 정보 수신 동의 일시',
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

-- user_service_terms 테이블 생성 (카카오 간편가입 약관 동의 내역 — (user_id, tag) 당 1행, 재로그인 시 갱신)
-- 기존 배포 DB 에는 수동 CREATE 필요 (배포 체크리스트 참조).
CREATE TABLE IF NOT EXISTS user_service_terms
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    tag        VARCHAR(100) NOT NULL COMMENT '카카오 간편가입 약관 태그',
    agreed     TINYINT(1)   NOT NULL COMMENT '동의 여부',
    agreed_at  DATETIME     NULL COMMENT '동의 일시 (카카오 동의창 기준)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_user_service_terms_user_tag (user_id, tag),

    CONSTRAINT fk_user_service_terms_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- admins 테이블 생성 (관리자 계정 — 서비스 유저와 무관한 독립 ID/PW 계정)
-- 계정 생성은 배포 환경변수 부트스트랩(AdminAccountInitializer) 또는 운영자의 직접 INSERT(BCrypt 해시)로 한다.
-- 직접 INSERT 시 해시는 기본 cost(10)로 생성할 것 — cost 가 다르면 로그인 타이밍 방어가 약해진다 (Admin javadoc 참고).
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
    store_code      VARCHAR(20)  NULL COMMENT '선택한 접수처의 원천 점포 코드 (cvs_stores 재조인용, 자유입력 등록분은 NULL)',
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
    status            VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING | PAYMENT_COLLECTING(C2C 입금 수집중) | CONFIRMED | CANCELLED(인원미달/미성사 자동취소) | HOST_CANCELLED(개최자 취소, 목록·상세 비노출)',
    finalized_at      DATETIME     NULL COMMENT '마감 판정(진행확정/취소) 시각. C2C 는 개최자 성사 확정 시각',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- C2C 플로우 병존(그랜드파더링) 컬럼. 기존 배포 DB 에는 수동 ALTER 필요 (docs/46 §2.3 — 배포 전 선행).
    flow_type         VARCHAR(10)  NOT NULL DEFAULT 'LEGACY' COMMENT 'LEGACY(즉시 입금+페이액션) | C2C(신청→확정→입금 직거래)',
    payment_due_at    DATETIME     NULL COMMENT 'C2C: 개최자 성사 확정 시 산정한 일괄 입금 기한',
    open_chat_url     VARCHAR(200) NULL COMMENT 'C2C: 개최자 오픈채팅 링크(선택) — 참여자 소통 채널',
    payment_bank      VARCHAR(50)  NULL COMMENT 'C2C: 확정 시점 개최자 계좌 스냅샷 — 은행 (확정 후 프로필 변경과 무관하게 안내 계좌 고정)',
    payment_account   VARCHAR(50)  NULL COMMENT 'C2C: 확정 시점 개최자 계좌 스냅샷 — 계좌번호',
    payment_holder    VARCHAR(50)  NULL COMMENT 'C2C: 확정 시점 개최자 계좌 스냅샷 — 예금주',

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
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id  BIGINT       NOT NULL,
    image_url    VARCHAR(500) NOT NULL COMMENT '이미지 URL',
    is_thumbnail TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '대표사진 여부 (분철당 최대 1장, 없으면 MIN(id) 폴백)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    buncheol_member_id    BIGINT       NOT NULL COMMENT '참여한 멤버 슬롯',
    participant_id        BIGINT       NOT NULL COMMENT '참여자',
    shipping_address_id   BIGINT       NULL COMMENT '선택한 배송지 (참조 배송지 삭제 시 NULL — 종료된 참여 한정)',
    amount                BIGINT       NOT NULL COMMENT '멤버 금액 (굿즈 가격, 배송비 제외)',
    shipping_fee          BIGINT       NOT NULL DEFAULT 0 COMMENT '배송비 (묶음당 1회만 부과; 묶음 첫 슬롯만 >0). 입금 총액 = amount + shipping_fee',
    refund_bank           VARCHAR(50)  NOT NULL COMMENT '환불 은행',
    refund_account        VARCHAR(50)  NOT NULL COMMENT '환불 계좌번호',
    refund_holder         VARCHAR(50)  NOT NULL COMMENT '환불 예금주',
    due_at                DATETIME     NULL COMMENT '입금 만료 시각. LEGACY=min(점유+30분, deadline) | C2C=성사 확정 시 일괄 산정(APPLIED 단계 NULL)',
    confirmed_at          DATETIME     NULL COMMENT '개최자 입금확인 시각',
    cancelled_at          DATETIME     NULL COMMENT '참여 취소 시각',
    cancel_reason         VARCHAR(30)  NULL COMMENT 'PAYMENT_TIMEOUT | BUNCHEOL_CANCELLED | USER_CANCELLED(C2C 자발 취소)',
    status                VARCHAR(30)  NOT NULL COMMENT 'AWAITING_PAYMENT | CONFIRMED | CANCELLED | APPLIED(C2C 신청) | PAYMENT_SENT(C2C 보냈어요)',
    -- 오픈 이벤트 배송비 환급(배송비 돌려받기). ELIGIBLE/EXPIRED 는 조회 시 파생하며 저장하지 않는다 (PaybackStatus javadoc 참고).
    payback_status        VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE | REQUESTED | COMPLETED | REJECTED (ELIGIBLE/EXPIRED 는 파생 전용)',
    payback_tweet_url     VARCHAR(255) NULL COMMENT '환급 신청 후기 트윗 URL (쿼리스트링 제거 정규화 저장)',
    payback_requested_at  DATETIME     NULL COMMENT '환급 신청(재신청 포함) 시각',
    payback_completed_at  DATETIME     NULL COMMENT '환급 입금 완료 시각',
    payback_reject_reason VARCHAR(200) NULL COMMENT '환급 반려 사유 (재신청 시 초기화)',
    payback_amount        BIGINT       NULL COMMENT '신청 시점 배송비 스냅샷 (환급액 고정)',
    -- 멤버 슬롯당 활성 참여 1건(선착순) 보장용 가상 컬럼: 활성 상태일 때만 멤버 슬롯 id 값을 갖고, 취소/만료되면 NULL 이 되어 슬롯이 다시 열린다.
    active_member_id      BIGINT GENERATED ALWAYS AS (
                              IF(status IN ('APPLIED', 'AWAITING_PAYMENT', 'PAYMENT_SENT', 'CONFIRMED'),
                                 buncheol_member_id, NULL)
                              ) STORED COMMENT '활성 상태일 때만 buncheol_member_id 값 (선착순 유니크용)',
    -- 활성 참여자 가상 컬럼. 과거 (buncheol_id, active_participant_id) 유니크로 분철당 1인 1참여를 강제했으나,
    -- C2C 다슬롯 허용(docs/46 §7.1-11)으로 유니크는 제거 — LEGACY 의 1인 1슬롯은 앱 가드(BCH-075)가 담당한다.
    active_participant_id BIGINT GENERATED ALWAYS AS (
                              IF(status IN ('APPLIED', 'AWAITING_PAYMENT', 'PAYMENT_SENT', 'CONFIRMED'),
                                 participant_id, NULL)
                              ) STORED COMMENT '활성 상태일 때만 participant_id 값 (LEGACY 중복 참여 가드 보조)',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- C2C 컬럼. 기존 배포 DB 에는 수동 ALTER 필요 (docs/46 §2.3 — 배포 전 선행).
    payment_sent_at       DATETIME     NULL COMMENT 'C2C: 참여자 "보냈어요" 마킹 시각 — 분쟁 증거, 반려·철회 후에도 보존',
    -- 분철 flow_type 비정규화 (조건부 INSERT 가 buncheols 에서 복사). generated column 은 타 테이블을 참조할 수
    -- 없어, LEGACY 전용 1인 1참여 유니크를 위해 참여 행에 내려받는다.
    flow_type             VARCHAR(10)  NOT NULL DEFAULT 'LEGACY' COMMENT '분철 flow_type 비정규화 — LEGACY 1인 1참여 유니크용',
    -- LEGACY 분철당 참여자 1명 보장용 가상 컬럼. C2C 는 다슬롯 허용(docs/46 §7.1-11)이라 LEGACY 조건이 붙는다.
    legacy_active_participant_id BIGINT GENERATED ALWAYS AS (
                              IF(flow_type = 'LEGACY' AND status IN ('AWAITING_PAYMENT', 'CONFIRMED'),
                                 participant_id, NULL)
                              ) STORED COMMENT 'LEGACY 활성 참여일 때만 participant_id 값 (분철당 중복 참여 방지용)',

    PRIMARY KEY (id),

    -- 멤버 슬롯당 활성 참여 1건 (선착순). 동시 참여 시 두 번째 INSERT 가 이 제약에 막혀 DuplicateKey 로 떨어진다.
    UNIQUE INDEX uq_participations_active_member (active_member_id),
    -- (제거됨) uq_participations_active_participant — C2C 다슬롯 허용으로 DROP (docs/46 §2.3-4).
    -- LEGACY 분철당 참여자 1명 (중복 참여 금지). 서비스 사전 체크의 check-then-insert 갭을 DB 가 최종 차단한다.
    -- C2C 참여는 legacy_active_participant_id 가 NULL 이라 이 유니크의 영향을 받지 않는다.
    UNIQUE INDEX uq_participations_legacy_active_participant (buncheol_id, legacy_active_participant_id),
    -- 분철별 상태 집계(확정 인원 카운트)·호스트 참여 목록 조회용
    INDEX idx_participations_buncheol_status (buncheol_id, status),
    -- 입금 만료 스케줄러 폴링(status='AWAITING_PAYMENT' AND due_at<=now)용
    INDEX idx_participations_status_due (status, due_at),
    -- 내 참여 목록(참여자별 최신순)용
    INDEX idx_participations_participant_created (participant_id, created_at DESC),
    -- 관리자 결제 목록(전체 참여 최신순) 커서 페이지네이션용. 기존 배포 DB 에는 수동 ALTER 필요
    -- (CREATE TABLE IF NOT EXISTS 는 기존 테이블에 인덱스를 추가하지 않는다).
    INDEX idx_participations_created (created_at DESC, id DESC),
    -- 같은 후기 트윗의 타 참여 중복 신청 방지 (서비스 사전 체크의 check-then-update 갭을 DB 가 최종 차단).
    -- NULL(미신청)은 MySQL 유니크에서 중복 허용이라 문제없다.
    UNIQUE INDEX uq_participations_payback_tweet_url (payback_tweet_url),
    -- 어드민 환급 신청 목록(신청 최신순) 커서 페이지네이션용.
    INDEX idx_participations_payback_requested (payback_status, payback_requested_at DESC, id DESC),

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
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id        BIGINT       NOT NULL COMMENT '참여 ID',
    shipping_method         VARCHAR(20)  NOT NULL COMMENT '배송 방식 스냅샷',
    store_name              VARCHAR(100) NOT NULL COMMENT '편의점 지점명 스냅샷',
    receiver_nickname       VARCHAR(20)  NOT NULL COMMENT '닉네임 스냅샷',
    receiver_phone_number   VARCHAR(15)  NOT NULL COMMENT '연락처 스냅샷',
    tracking_number         VARCHAR(100) NULL COMMENT '운송장 번호',
    tracking_registered_at  DATETIME     NULL COMMENT '운송장 등록 시각',
    delivered_at            DATETIME     NULL COMMENT '배송 완료 시각',
    received_at             DATETIME     NULL COMMENT '사용자 수령 완료 시각',
    -- 미수령 독촉 알림 1회 발송 dedup 마커. 기존 배포 DB 에는 수동 ALTER 필요.
    pickup_reminder_sent_at DATETIME     NULL COMMENT '미수령 독촉 알림 발송 시각',
    status                  VARCHAR(20)  NOT NULL DEFAULT 'SNAPSHOTTED' COMMENT 'SNAPSHOTTED | SHIPPING | DELIVERED | RECEIVED',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_deliveries_participation_id (participation_id),
    INDEX idx_deliveries_status (status),
    -- 배송 추적 콜백의 운송장 조회용 (관리자 벌크 등록으로 한 운송장에 다건 매핑). 기존 배포 DB 에는 수동 ALTER 필요.
    INDEX idx_deliveries_tracking (tracking_number, shipping_method),

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

-- cvs_stores 테이블 생성 (편의점 택배 접수처 마스터 — 배송지 등록 화면의 지점 검색용)
-- 크롤러(buncheoleasy-crawler)가 수집·정규화해 S3 에 게시한 스냅샷을 CvsStoreSyncScheduler 가 매주 반영한다.
CREATE TABLE IF NOT EXISTS cvs_stores
(
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    brand      VARCHAR(10)    NOT NULL COMMENT 'GS25 | CU',
    store_code VARCHAR(20)    NOT NULL COMMENT '원천(브랜드) 점포 코드',
    name       VARCHAR(100)   NOT NULL COMMENT '지점명',
    tel        VARCHAR(20)    NULL,
    sido       VARCHAR(20)    NULL COMMENT '정규화된 축약 시도명 (서울/경기/...)',
    address    VARCHAR(255)   NOT NULL COMMENT '도로명 주소',
    post_no    VARCHAR(6)     NULL COMMENT '우편번호',
    latitude   DECIMAL(10, 7) NOT NULL COMMENT 'WGS84 위도',
    longitude  DECIMAL(11, 7) NOT NULL COMMENT 'WGS84 경도',
    receive_yn TINYINT(1)     NOT NULL COMMENT '택배 접수(보내기) 가능',
    pickup_yn  TINYINT(1)     NOT NULL COMMENT '택배 픽업(수령) 가능',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 갱신은 JPA(동기화 배치)가 하지만, 로컬 적재 스크립트 등 JPA 밖 쓰기도 갱신 시각이 남게 DB 레벨로 보강
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 동기화 배치의 (brand, store_code) upsert 기준 — 행의 id 는 재발급되지 않으며 검색 keyset 커서가 이에 의존한다
    UNIQUE KEY uk_cvs_stores_brand_code (brand, store_code),
    -- 접수처 검색 쿼리(WHERE pickup_yn AND brand ORDER BY id) 커버용
    INDEX idx_cvs_stores_pickup_brand (pickup_yn, brand, id),
    -- 향후 좌표 범위(주변 접수처) 조회 대비
    INDEX idx_cvs_stores_coord (latitude, longitude)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

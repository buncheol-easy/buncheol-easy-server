-- ⚠️ 스키마 변경 운영 규칙 — expand-contract (루트 docs/39)
-- 블루-그린 배포는 구·신 버전이 같은 DB 를 수십 초 동시에 본다. 파괴적 변경을 코드 배포와 한 릴리스에
-- 묶으면 전환 창에서 구버전이 죽는다.
-- ① expand(컬럼·테이블 추가)는 배포 전에 수동 ALTER 로 먼저 반영한다. 이 파일은 전부 CREATE TABLE
--   IF NOT EXISTS 라 기존 테이블에 컬럼·인덱스를 추가해 주지 않는다 — 아래 "기존 배포 DB 에는 수동
--   ALTER 필요" 주석이 그 지점이다. 빠뜨리면 헬스는 통과하고 전환 직후부터 500 이 난다.
-- ② contract(제거·rename·NOT NULL 강제)는 그걸 안 쓰는 코드가 완전히 내려간 다음 릴리스에서 한다.
-- ③ rename 은 "새 컬럼 추가 → 양쪽 쓰기 → 구 컬럼 제거" 3단계로 쪼갠다.

CREATE TABLE IF NOT EXISTS users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL COMMENT '소셜 로그인 제공자 (KAKAO, GOOGLE, APPLE …)',
    provider_id         VARCHAR(100) NOT NULL COMMENT '소셜 제공자 고유 ID',
    email               VARCHAR(320) NOT NULL COMMENT '소셜 계정 이메일',
    nickname            VARCHAR(20)  NOT NULL COMMENT '닉네임',
    -- 기존 회원은 값이 없고 마이페이지에서 수시 입력한다 (신규 가입은 FE 에서 필수 강제).
    -- 기존 배포 DB 에는 수동 ALTER 필요.
    name                VARCHAR(30)  NULL COMMENT '실명 (입금 대조·배송 연락 참조)',
    phone_number        VARCHAR(15)  NULL COMMENT '연락처',
    settlement_bank     VARCHAR(50)  NULL COMMENT '정산 은행',
    settlement_account  VARCHAR(50)  NULL COMMENT '정산 계좌번호',
    settlement_holder   VARCHAR(50)  NULL COMMENT '정산 예금주',
    profile_completed   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '프로필 설정 완료 여부',
    -- 개최 오픈 전 운영 지정 계정만 true. 부여는 DB 직접 UPDATE 로 한다.
    -- 기존 배포 DB 에는 수동 ALTER 필요.
    can_host            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '분철 개최 허용 여부',
    -- NULL = 미동의/철회. 광고성 정보는 동의 일시 기록·2년 주기 재확인 의무가 있어 boolean 대신 일시로 저장한다.
    -- 기존 배포 DB 에는 수동 ALTER 필요.
    marketing_agreed_at DATETIME     NULL COMMENT '마케팅 정보 수신 동의 일시',
    -- 카카오 선택 동의 항목. 개최자 성인 확인 참조용이며 재로그인 시 최신값으로 갱신한다. NULL = 미동의/미수집.
    -- 기존 배포 DB 에는 수동 ALTER 필요 (docs/50).
    age_range           VARCHAR(10)  NULL COMMENT '카카오 연령대 (예: 20~29)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          DATETIME     NULL COMMENT '회원탈퇴 soft delete',

    -- 탈퇴 계정을 유니크 대상에서 빼는 가상 컬럼 — deleted_at 이 NULL 일 때만 값을 갖는다.
    _active_provider    VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, provider, NULL)) VIRTUAL,
    _active_provider_id VARCHAR(100) GENERATED ALWAYS AS (IF(deleted_at IS NULL, provider_id, NULL)) VIRTUAL,
    _active_nickname    VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, nickname, NULL)) VIRTUAL,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_users_active_social_account (_active_provider, _active_provider_id),
    UNIQUE INDEX uq_users_active_nickname (_active_nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 카카오 간편가입 약관 동의 내역 — (user_id, tag) 당 1행이며 재로그인 시 갱신한다.
-- 기존 배포 DB 에는 수동 CREATE 필요.
CREATE TABLE IF NOT EXISTS user_service_terms
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    tag        VARCHAR(100) NOT NULL COMMENT '카카오 간편가입 약관 태그',
    agreed     TINYINT(1)   NOT NULL COMMENT '동의 여부',
    agreed_at  DATETIME     NULL COMMENT '동의 일시 (카카오 동의창 기준)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_user_service_terms_user_tag (user_id, tag),

    CONSTRAINT fk_user_service_terms_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 관리자 계정 — 서비스 유저와 무관한 독립 ID/PW 계정이다.
-- 생성은 배포 환경변수 부트스트랩(AdminAccountInitializer) 또는 운영자 직접 INSERT 로 한다. 직접 INSERT 시
-- BCrypt 해시는 기본 cost(10)로 만들 것 — cost 가 다르면 로그인 타이밍 방어가 약해진다 (Admin javadoc).
-- 관리자 토큰은 role claim(ADMIN)으로 유저 토큰과 구분된다. 유저 API 는 hasRole(USER) 라 admins.id 와
-- users.id 가 겹쳐도 위장할 수 없다.
CREATE TABLE IF NOT EXISTS admins
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50)  NOT NULL COMMENT '관리자 로그인 ID',
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_admins_login_id (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

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
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_shipping_addresses_user_id (user_id),
    UNIQUE INDEX uq_shipping_addresses_user_method_store (user_id, shipping_method, store_name),

    CONSTRAINT fk_shipping_addresses_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `groups`
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '그룹명',
    image       VARCHAR(500) NULL COMMENT '이미지 URL',
    -- 공백·구두점 제거 + 소문자 정규화. 검색어 쪽은 SearchText.normalize 가 같은 규칙으로 수행한다.
    -- 그룹은 SQL 로 직접 시드되므로(애플리케이션 쓰기 경로 없음) DB 가 계산하게 해 INSERT 누락을 원천 차단한다.
    search_name VARCHAR(100) GENERATED ALWAYS AS (LOWER(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        name, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
    )) STORED COMMENT '검색용 정규화 그룹명',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_groups_name (name),
    INDEX idx_groups_search_name (search_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS group_members
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    group_id    BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL COMMENT '멤버명',
    image       VARCHAR(500) NULL COMMENT '이미지 URL',
    -- `groups`.search_name 과 동일 규칙. 멤버명 검색("장원영")이 공백·구두점과 무관하게 걸리도록 한다.
    search_name VARCHAR(100) GENERATED ALWAYS AS (LOWER(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        name, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
    )) STORED COMMENT '검색용 정규화 멤버명',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_group_members_group_id (group_id),
    INDEX idx_group_members_name (name),
    INDEX idx_group_members_search_name (search_name),

    CONSTRAINT fk_group_members_group
        FOREIGN KEY (group_id)
            REFERENCES `groups` (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 그룹명 교차 표기 검색용 별칭. 그룹명은 공식 표기 하나만 저장되는데(188개 중 영문 128 / 한글 61)
-- 사용자는 다른 표기로 검색한다. "IVE"→"아이브" 같은 교차 표기는 로마자 변환으로 역산되지 않고
-- ("아이브"의 로마자는 "aibeu"이지 "ive"가 아니다) 팬덤 축약어는 더더욱 규칙이 없어 데이터로만 풀 수 있다.
CREATE TABLE IF NOT EXISTS group_aliases
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    group_id     BIGINT       NOT NULL,
    alias        VARCHAR(100) NOT NULL COMMENT '별칭 원문',
    -- `groups`.search_name 과 동일 규칙. "i-dle" 과 "idle" 이 같은 별칭으로 접히게 한다.
    search_alias VARCHAR(100) GENERATED ALWAYS AS (LOWER(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        alias, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
    )) STORED COMMENT '검색용 정규화 별칭',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 정규화 후 1자인 별칭은 부분일치에서 거의 모든 검색어에 걸려 결과를 오염시킨다. 등록 자체를 막는다.
    CONSTRAINT ck_group_aliases_min_length CHECK (CHAR_LENGTH(search_alias) >= 2),
    -- 같은 그룹에 정규화 결과가 같은 별칭("i-dle" 과 "idle")을 중복 등록하지 않는다.
    -- 그룹 간 중복은 막지 않는다 — 여러 그룹이 정당하게 공유하는 별칭이 있다.
    UNIQUE KEY uk_group_aliases_group_search (group_id, search_alias),

    INDEX idx_group_aliases_search_alias (search_alias),

    CONSTRAINT fk_group_aliases_group
        FOREIGN KEY (group_id)
            REFERENCES `groups` (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buncheols
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    host_id           BIGINT       NOT NULL COMMENT '개최자',
    group_id          BIGINT       NOT NULL COMMENT '대상 그룹',
    title             VARCHAR(200) NOT NULL COMMENT '분철 제목',
    -- `groups`.search_name 과 동일 규칙. "아이브앨범" 으로도 "아이브 앨범 분철" 이 검색되게 한다.
    search_title      VARCHAR(200) GENERATED ALWAYS AS (LOWER(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        title, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
    )) STORED COMMENT '검색용 정규화 제목',
    description       TEXT         NULL COMMENT '분철 설명',
    purchase_site     VARCHAR(200) NOT NULL COMMENT '구매처',
    deadline          DATETIME     NOT NULL COMMENT '분철 마감일',
    min_headcount     INT          NOT NULL COMMENT '분철 진행 최소 인원',
    gs25_shipping_fee INT          NULL COMMENT 'GS25반값택배 배송비',
    cu_shipping_fee   INT          NULL COMMENT 'CU반값택배 배송비',
    status            VARCHAR(30)  NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING | PAYMENT_COLLECTING(C2C 입금 수집중) | CONFIRMED | CANCELLED(인원미달/미성사 자동취소) | HOST_CANCELLED(개최자 취소, 목록·상세 비노출)',
    finalized_at      DATETIME     NULL COMMENT '마감 판정(진행확정/취소) 시각. C2C 는 개최자 성사 확정 시각',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 이하 C2C 플로우 병존(그랜드파더링) 컬럼.
    -- 기존 배포 DB 에는 수동 ALTER 필요 (docs/46 §2.3).
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
    -- 공개 목록 '모집중' 그룹 조회(status='RECRUITING', createdAt DESC, id DESC) 커서 페이지네이션용
    INDEX idx_buncheols_status_created (status, created_at DESC, id DESC),
    -- groupId 필터 + 커서 조합용 (idx_buncheols_group_id 만으로는 정렬을 인덱스로 커버 불가)
    INDEX idx_buncheols_group_created (group_id, created_at DESC, id DESC),
    -- 자동 마감 스케줄러 폴링(status='RECRUITING' AND deadline <= now)과 공개 목록 '마감' 그룹 조회 겸용.
    -- groupId 필터 + '마감' 조합만 filesort 로 떨어진다 (그룹별 마감분이 적어 수용).
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
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    buncheol_id BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL COMMENT '대상 멤버',
    price       BIGINT      NOT NULL COMMENT '멤버 1명당 고정 금액 (100원 단위)',
    -- 코드 수명과 무관한 슬롯 접근 정책. 기존 배포 DB 에는 수동 ALTER 필요.
    access_type VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN=선착순 | CODE_ONLY=참여 코드 보유자만',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_buncheol_members_buncheol_id (buncheol_id),
    UNIQUE INDEX uq_buncheol_members_buncheol_member (buncheol_id, member_id),
    -- memberId 단독 검색(분철 목록의 memberId 서브쿼리)용. uq_(buncheol_id, member_id) 는 여기에 쓸 수 없다.
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

-- participation_bundles 테이블 생성 (참여 묶음 — docs/70 §3)
--
-- 현실의 돈은 묶음 단위다: 이체 1회 · 배송비 1회 · 택배 1개. 그런데 모델이 슬롯 단위라
-- 배송비·배송지·환불계좌·기한이 슬롯 행에 흩어져 있었고, 그 행이 취소되면 값도 같이 죽었다
-- (docs/62 M-01 — prod 에서 실제로 확인됨). 이 테이블이 그것들의 정본이 된다.
--
-- ⛔ 활성 묶음 유니크는 만들지 않는다 (docs/71 §8-3). 추가 모집·재신청이 "새 묶음" 이어야 하므로
--    한 사람이 한 분철에 활성 묶음을 2개 가질 수 있어야 한다. 중복 방지는 앱 가드가 하고,
--    LEGACY 1인 1슬롯 보호는 participations.uq_participations_legacy_active_participant 가 그대로 계속한다.
--    (그래서 active_participant_id 같은 생성 컬럼도 필요 없다.)
CREATE TABLE IF NOT EXISTS participation_bundles
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    buncheol_id         BIGINT      NOT NULL COMMENT '대상 분철',
    participant_id      BIGINT      NOT NULL COMMENT '참여자',
    shipping_address_id BIGINT      NULL COMMENT '배송지 — 묶음당 1개(택배 1개)',
    shipping_fee        BIGINT      NOT NULL DEFAULT 0 COMMENT '배송비 — 묶음이 소유하며 묶음당 1회. 슬롯이 취소돼도 불변',
    -- ⚠️ DEFAULT 를 두지 않는다. RefundAccount 는 record 라 JPA 가 조회 시에도 생성자를 태우고
    -- (BankAccount javadoc 참조) 그 생성자가 빈 값을 거부한다 — 빈 값으로 채워진 묶음은 영원히 읽을 수 없다.
    -- DEFAULT 가 없으면 값을 빠뜨린 INSERT 가 그 자리에서 실패하므로, 읽기 불가 행이 만들어지지 않는다.
    refund_bank         VARCHAR(50) NOT NULL COMMENT '환불 은행',
    refund_account      VARCHAR(50) NOT NULL COMMENT '환불 계좌번호',
    refund_holder       VARCHAR(50) NOT NULL COMMENT '환불 예금주 = 개최자 통장 대조 키(입금자명)',
    due_at              DATETIME    NULL COMMENT '입금 기한. C2C 는 자동 취소하지 않는다 — 이 시각부터 개최자 「제외」가 열린다 (docs/71 §8-1)',
    payment_sent_at     DATETIME    NULL COMMENT '참여자 「보냈어요」 마킹 시각 (묶음 1회)',
    closed_at           DATETIME    NULL COMMENT '묶음 종료(활성 슬롯 0) 시각. NULL = 활성',
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 슬롯이 자기 묶음과 (분철, 사람)에서 어긋나지 않도록 하는 복합 FK 의 참조 대상. 지금은 참조하지 않지만
    -- 나중에 붙일 수 있게 열어 둔다 — 나중에 만들면 기존 행을 전부 재검증해야 한다.
    UNIQUE INDEX uk_participation_bundles_id_keys (id, buncheol_id, participant_id),
    -- "이 사람의 이 분철 활성 묶음" 조회용. 활성 유니크를 두지 않기로 해 이 조회를 받쳐 줄 인덱스가 따로 필요하다.
    INDEX idx_participation_bundles_buncheol_participant (buncheol_id, participant_id),
    -- 내 참여 목록(참여자별 최신순)용
    INDEX idx_participation_bundles_participant_created (participant_id, created_at DESC),

    CONSTRAINT fk_participation_bundles_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_participation_bundles_user
        FOREIGN KEY (participant_id)
            REFERENCES users (id),
    CONSTRAINT fk_participation_bundles_shipping_address
        FOREIGN KEY (shipping_address_id)
            REFERENCES shipping_addresses (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participations
(
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    buncheol_id                  BIGINT       NOT NULL,
    buncheol_member_id           BIGINT       NOT NULL COMMENT '참여한 멤버 슬롯',
    participant_id               BIGINT       NOT NULL COMMENT '참여자',
    -- 소속 묶음 (docs/70 §3). P1 은 추가만 — 아직 아무도 읽지 않는다. 백필 후 NOT NULL 로 조인다.
    bundle_id                    BIGINT       NULL COMMENT '소속 묶음',
    -- 🔴 배송지·배송비도 죽은 컬럼이다 — 정본이 participation_bundles 로 옮겨갔고 INSERT 목록에서 빠졌다.
    -- ⚠️ "NULL = 이상"·"0 = 무료" 판정을 여기에 세우지 말 것. 특히 배송지 삭제 가드가 이 칸만 보면
    -- 신규 행에서 전 건 false 가 되어 배송 대기 중인 주소가 지워지고, ON DELETE SET NULL 이 정본까지 비운다.
    shipping_address_id          BIGINT       NULL COMMENT '[P4 에서 삭제] 선택한 배송지 — 정본은 participation_bundles. 신규 행은 전부 NULL',
    amount                       BIGINT       NOT NULL COMMENT '멤버 금액 (굿즈 가격, 배송비 제외)',
    shipping_fee                 BIGINT       NOT NULL DEFAULT 0 COMMENT '[P4 에서 삭제] 배송비 — 정본은 participation_bundles. 신규 행은 전부 0',
    -- 🔴 P2-c 이후 이 세 칸은 죽은 컬럼이다 — 정본이 participation_bundles 로 옮겨갔고 INSERT 목록에서 빠졌다.
    -- **신규 행은 전부 NULL 이다.** 값이 있는 것은 P2-c 배포 이전에 만들어진 행뿐이다.
    -- ⚠️ 그러므로 "NULL = 이상" 이라는 판정을 여기에 세우지 말 것. 계좌를 확인하려면 묶음을 봐야 한다.
    -- 컬럼 자체는 P4 에서 삭제한다 — 단, 블루-그린 전환 창에서 구 색이 아직 읽으므로 P2-c 와 같은 릴리스에
    -- 넣으면 안 된다(docs/39).
    -- ⚠️ DEFAULT '' 로 완화해서는 안 된다 — RefundAccount 는 record 라 JPA 가 조회 시에도 생성자를 태우고
    -- 그 생성자가 빈 값을 거부해(BankAccount javadoc) 그 행을 읽는 것 자체가 깨진다. 세 컬럼이 모두 NULL
    -- 이면 Hibernate 는 embeddable 을 null 로 두고 생성자를 타지 않는다.
    refund_bank                  VARCHAR(50)  NULL COMMENT '[P4 에서 삭제] 환불 은행 — 정본은 participation_bundles. P2-c 이후 신규 행은 전부 NULL',
    refund_account               VARCHAR(50)  NULL COMMENT '[P4 에서 삭제] 환불 계좌번호 — 정본은 participation_bundles. P2-c 이후 신규 행은 전부 NULL',
    refund_holder                VARCHAR(50)  NULL COMMENT '[P4 에서 삭제] 환불 예금주 — 정본은 participation_bundles. P2-c 이후 신규 행은 전부 NULL',
    due_at                       DATETIME     NULL COMMENT '입금 만료 시각. LEGACY=min(점유+30분, deadline) | C2C=성사 확정 시 일괄 산정(APPLIED 단계 NULL)',
    confirmed_at                 DATETIME     NULL COMMENT '개최자 입금확인 시각',
    cancelled_at                 DATETIME     NULL COMMENT '참여 취소 시각',
    cancel_reason                VARCHAR(30)  NULL COMMENT 'PAYMENT_TIMEOUT | BUNCHEOL_CANCELLED | USER_CANCELLED(C2C 자발 취소)',
    status                       VARCHAR(30)  NOT NULL COMMENT 'AWAITING_PAYMENT | CONFIRMED | CANCELLED | APPLIED(C2C 신청) | PAYMENT_SENT(C2C 보냈어요)',
    -- 오픈 이벤트 배송비 환급. ELIGIBLE/EXPIRED 는 조회 시 파생하며 저장하지 않는다 (PaybackStatus javadoc).
    payback_status               VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE | REQUESTED | COMPLETED | REJECTED (ELIGIBLE/EXPIRED 는 파생 전용)',
    payback_tweet_url            VARCHAR(255) NULL COMMENT '환급 신청 후기 트윗 URL (쿼리스트링 제거 정규화 저장)',
    payback_requested_at         DATETIME     NULL COMMENT '환급 신청(재신청 포함) 시각',
    payback_completed_at         DATETIME     NULL COMMENT '환급 입금 완료 시각',
    payback_reject_reason        VARCHAR(200) NULL COMMENT '환급 반려 사유 (재신청 시 초기화)',
    payback_amount               BIGINT       NULL COMMENT '신청 시점 배송비 스냅샷 (환급액 고정)',
    -- 멤버 슬롯당 활성 참여 1건(선착순) 보장용. 취소·만료되면 NULL 이 되어 슬롯이 다시 열린다.
    active_member_id             BIGINT GENERATED ALWAYS AS (
                                     IF(status IN ('APPLIED', 'AWAITING_PAYMENT', 'PAYMENT_SENT', 'CONFIRMED'),
                                        buncheol_member_id, NULL)
                                     ) STORED COMMENT '활성 상태일 때만 buncheol_member_id 값 (선착순 유니크용)',
    -- 과거 (buncheol_id, active_participant_id) 유니크로 분철당 1인 1참여를 강제했으나, C2C 다슬롯
    -- 허용(docs/46 §7.1-11)으로 유니크는 제거했다 — LEGACY 의 1인 1슬롯은 앱 가드(BCH-075)가 담당한다.
    active_participant_id        BIGINT GENERATED ALWAYS AS (
                                     IF(status IN ('APPLIED', 'AWAITING_PAYMENT', 'PAYMENT_SENT', 'CONFIRMED'),
                                        participant_id, NULL)
                                     ) STORED COMMENT '활성 상태일 때만 participant_id 값 (LEGACY 중복 참여 가드 보조)',
    created_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 이하 C2C 컬럼. 기존 배포 DB 에는 수동 ALTER 필요 (docs/46 §2.3).
    payment_sent_at              DATETIME     NULL COMMENT 'C2C: 참여자 "보냈어요" 마킹 시각 — 분쟁 증거, 반려·철회 후에도 보존',
    payment_rejected_at          DATETIME     NULL COMMENT 'C2C: 개최자 "입금 못 찾음" 반려 시각 — 재마킹 시 NULL 초기화(최근 상태가 반려인지 판정). 셀프 철회는 NULL 유지',
    -- 분철 flow_type 비정규화 (조건부 INSERT 가 buncheols 에서 복사). generated column 은 타 테이블을
    -- 참조할 수 없어, LEGACY 전용 1인 1참여 유니크를 위해 참여 행에 내려받는다.
    flow_type                    VARCHAR(10)  NOT NULL DEFAULT 'LEGACY' COMMENT '분철 flow_type 비정규화 — LEGACY 1인 1참여 유니크용',
    -- LEGACY 분철당 참여자 1명 보장용. C2C 는 다슬롯 허용(docs/46 §7.1-11)이라 LEGACY 조건이 붙는다.
    legacy_active_participant_id BIGINT GENERATED ALWAYS AS (
                                     IF(flow_type = 'LEGACY' AND status IN ('AWAITING_PAYMENT', 'CONFIRMED'),
                                        participant_id, NULL)
                                     ) STORED COMMENT 'LEGACY 활성 참여일 때만 participant_id 값 (분철당 중복 참여 방지용)',

    PRIMARY KEY (id),

    -- 멤버 슬롯당 활성 참여 1건(선착순). 동시 참여 시 두 번째 INSERT 가 여기 막혀 DuplicateKey 로 떨어진다.
    UNIQUE INDEX uq_participations_active_member (active_member_id),
    -- (제거됨) uq_participations_active_participant — C2C 다슬롯 허용으로 DROP (docs/46 §2.3-4).
    -- LEGACY 분철당 참여자 1명. 서비스 사전 체크의 check-then-insert 갭을 DB 가 최종 차단한다.
    -- C2C 참여는 legacy_active_participant_id 가 NULL 이라 영향을 받지 않는다.
    UNIQUE INDEX uq_participations_legacy_active_participant (buncheol_id, legacy_active_participant_id),
    -- 분철별 상태 집계(확정 인원 카운트)·호스트 참여 목록 조회용
    INDEX idx_participations_buncheol_status (buncheol_id, status),
    -- 입금 만료 스케줄러 폴링용. C2C 는 자동 만료하지 않으므로(docs/70 결정 9) flow_type 을 등가조건으로
    -- 함께 받는다 — 컬럼 순서가 (동등, 동등, 범위+정렬) 이라야 due_at 이 인덱스로 이어진다.
    -- ⚠️ 기존 배포 DB 에는 수동 ALTER 필요 (CREATE TABLE IF NOT EXISTS 는 인덱스를 추가하지 않는다).
    INDEX idx_participations_status_flow_due (status, flow_type, due_at),
    -- 내 참여 목록(참여자별 최신순)용
    INDEX idx_participations_participant_created (participant_id, created_at DESC),
    -- 묶음의 활성 슬롯 조회·집계용 (묶음 확인·제외·종료 판정)
    INDEX idx_participations_bundle_status (bundle_id, status),
    -- 관리자 결제 목록(전체 참여 최신순) 커서 페이지네이션용. 기존 배포 DB 에는 수동 ALTER 필요.
    INDEX idx_participations_created (created_at DESC, id DESC),
    -- 같은 후기 트윗의 타 참여 중복 신청 방지용 (check-then-update 갭을 DB 가 최종 차단).
    -- NULL(미신청)은 MySQL 유니크에서 중복 허용이라 문제없다.
    UNIQUE INDEX uq_participations_payback_tweet_url (payback_tweet_url),
    -- 어드민 환급 신청 목록(신청 최신순) 커서 페이지네이션용
    INDEX idx_participations_payback_requested (payback_status, payback_requested_at DESC, id DESC),

    -- ⚠️ CASCADE 가 아니라 SET NULL 이다. 묶음은 슬롯에서 파생된 것이지 슬롯의 소유자가 아니다.
    -- CASCADE 면 백필을 다시 돌리려고 DELETE FROM participation_bundles 하는 순간 참여 전 행이,
    -- 이어서 deliveries 까지 연쇄 삭제된다. bundle_id 를 NOT NULL 로 조이는 P2 에서 RESTRICT 로 승격한다.
    CONSTRAINT fk_participations_bundle
        FOREIGN KEY (bundle_id)
            REFERENCES participation_bundles (id) ON DELETE SET NULL,
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

CREATE TABLE IF NOT EXISTS participation_codes
(
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    code                  VARCHAR(16) NOT NULL COMMENT '참여 코드 (Crockford base32 대문자 정규화 저장)',
    buncheol_id           BIGINT      NOT NULL,
    buncheol_member_id    BIGINT      NULL COMMENT '바인딩된 멤버 슬롯 (NULL=분철 단위 코드)',
    issued_to             VARCHAR(50) NULL COMMENT '발급 대상 운영 메모 (X 핸들 등) — 재발급 이력 추적용',
    expires_at            DATETIME    NOT NULL COMMENT '만료 시각 (발급 시 필수 지정)',
    revoked_at            DATETIME    NULL COMMENT '운영자 폐기 시각 (재발급 시 이전 코드를 여기서 닫는다)',
    used_at               DATETIME    NULL COMMENT '참여에 사용된 시각 (1회용)',
    used_participation_id BIGINT      NULL COMMENT '이 코드로 생성된 참여',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_participation_codes_code (code),
    -- 슬롯당 유효 코드 1개는 유니크가 아니라 앱 가드로 지킨다 (ParticipationCode javadoc).
    INDEX idx_participation_codes_member (buncheol_member_id, id DESC),
    INDEX idx_participation_codes_buncheol (buncheol_id, id DESC),

    CONSTRAINT fk_participation_codes_buncheol
        FOREIGN KEY (buncheol_id)
            REFERENCES buncheols (id) ON DELETE CASCADE,
    CONSTRAINT fk_participation_codes_member
        FOREIGN KEY (buncheol_member_id)
            REFERENCES buncheol_members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deliveries
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id        BIGINT       NOT NULL COMMENT '묶음 대표 슬롯 (P4 에서 DROP — 정본은 bundle_id)',
    -- 1묶음 = 1택배 = 배송 1행 (docs/70 결정 4). 생성·조회 모두 이 칸이 기준이다.
    -- 전환 이전 다슬롯 묶음만 아직 여러 건이라, 병합 후 NOT NULL + UNIQUE 로 조인다.
    bundle_id               BIGINT       NULL COMMENT '소속 묶음 = 택배 1개',
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
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE INDEX uq_deliveries_participation_id (participation_id),
    -- 백필 전이라 아직 유니크가 아니다. 백필·검증 후 uq_deliveries_bundle 로 승격한다.
    INDEX idx_deliveries_bundle (bundle_id),
    INDEX idx_deliveries_status (status),
    -- 배송 추적 콜백의 운송장 조회용 (관리자 벌크 등록으로 한 운송장에 다건 매핑).
    -- 기존 배포 DB 에는 수동 ALTER 필요.
    INDEX idx_deliveries_tracking (tracking_number, shipping_method),

    CONSTRAINT fk_deliveries_bundle
        FOREIGN KEY (bundle_id)
            REFERENCES participation_bundles (id) ON DELETE SET NULL,
    CONSTRAINT fk_deliveries_participation
        FOREIGN KEY (participation_id)
            REFERENCES participations (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

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

-- 검색창 최근 검색어 — 사용자가 친 단어를 단일 컬럼에 보관한다.
-- 프론트가 그룹·멤버 name → id 변환을 책임지므로, 서버는 검색 요청의 (keyword|groupId|memberId)
-- 중 무엇이 와도 사용자가 입력한 텍스트 1개로 normalize 해 저장한다.
CREATE TABLE IF NOT EXISTS user_recent_searches
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL COMMENT '검색 사용자',
    keyword    VARCHAR(100) NOT NULL COMMENT '사용자가 검색창에 친 텍스트 (group.name / member.name normalize 포함)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 사용자별 최신순 조회 + 7개 초과 정리(offset) 쿼리 겸용
    INDEX idx_user_recent_searches_user_created (user_id, created_at DESC, id DESC),

    CONSTRAINT fk_user_recent_searches_user
        FOREIGN KEY (user_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 수신함 — 공지와 알림을 한 테이블로 관리한다.
-- 공지(NOTICE)는 전체 대상이라 recipient_id 가 NULL, 알림(NOTIFICATION)은 수신자별 1:1 로 생성한다.
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
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 본인 알림 피드(recipient_id = userId, pinned = false) 커서 페이지네이션용
    INDEX idx_inbox_recipient_created (recipient_id, created_at DESC, id DESC),
    -- 공지 피드(pinned = false)·상단 고정 공지(pinned = true) 조회용
    INDEX idx_inbox_type_pinned_created (type, pinned, created_at DESC, id DESC),
    -- 홈 배너 조회용 (배너 등록 공지 = banner_image_url IS NOT NULL 시크)
    INDEX idx_inbox_banner (banner_image_url),

    CONSTRAINT fk_inbox_messages_recipient
        FOREIGN KEY (recipient_id)
            REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 편의점 택배 접수처 마스터 — 배송지 등록 화면의 지점 검색용.
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
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 동기화 배치의 upsert 기준 — 행의 id 는 재발급되지 않으며 검색 keyset 커서가 이에 의존한다.
    UNIQUE KEY uk_cvs_stores_brand_code (brand, store_code),
    -- 접수처 검색 쿼리(WHERE pickup_yn AND brand ORDER BY id) 커버용
    INDEX idx_cvs_stores_pickup_brand (pickup_yn, brand, id),
    -- 향후 좌표 범위(주변 접수처) 조회 대비
    INDEX idx_cvs_stores_coord (latitude, longitude)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- feat/아이돌-브라우즈-검색-정규화 배포 전 prod/staging 수동 실행 필수.
-- groups / group_members / buncheols 는 기존 테이블이라 schema.sql 의 CREATE TABLE IF NOT EXISTS 로는 컬럼이 생기지 않는다.
-- 누락 시 검색·그룹 조회가 Unknown column 'search_name' 으로 500 (앱은 정상 기동해 헬스체크로 안 잡힘).
--
-- STORED 생성 컬럼이라 ALTER 는 테이블 재작성(ALGORITHM=COPY)을 수반한다. 세 테이블 모두 수천 행 규모라
-- 실행은 수 초 내로 끝나지만, 쓰기 잠금이 걸리므로 트래픽이 적은 시간대에 실행한다.
-- 기존 행의 값은 ALTER 시점에 DB 가 계산해 채우므로 별도 백필이 필요 없다.
--
-- 정규화 규칙(공백·전각공백·. _ - ( ) [ ] · 제거 후 소문자)은 Java SearchText.normalize 및
-- 프론트 normalizeGroupSearchText 와 반드시 동일해야 한다. 셋 중 하나만 바뀌면 검색이 조용히 어긋난다.
--
-- ⚠ 실행 시 클라이언트 접속 charset 을 반드시 utf8mb4 로 지정한다:
--     mysql --default-character-set=utf8mb4 -u... DB < 이_파일.sql
--   기본값이 latin1 인 클라이언트(docker exec mysql 등)로 한글을 INSERT 하면 원본 컬럼은 왕복 변환으로 멀쩡해 보이지만
--   생성 컬럼의 LOWER() 가 잘못된 내부 표현에 적용돼 search_name 만 깨진다 ('아이브 앨범' → '아이븜앨범').
--   같은 이유로 groups/group_members 시드 SQL 도 utf8mb4 클라이언트로 실행해야 한다.

ALTER TABLE `groups`
    ADD COLUMN search_name VARCHAR(100)
        GENERATED ALWAYS AS (LOWER(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
        )) STORED
        COMMENT '검색용 정규화 그룹명'
        AFTER image,
    ADD INDEX idx_groups_search_name (search_name);

ALTER TABLE group_members
    ADD COLUMN search_name VARCHAR(100)
        GENERATED ALWAYS AS (LOWER(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
        )) STORED
        COMMENT '검색용 정규화 멤버명'
        AFTER image,
    ADD INDEX idx_group_members_search_name (search_name);

ALTER TABLE buncheols
    ADD COLUMN search_title VARCHAR(200)
        GENERATED ALWAYS AS (LOWER(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            title, ' ', ''), '　', ''), '.', ''), '_', ''), '-', ''), '(', ''), ')', ''), '[', ''), ']', ''), '·', '')
        )) STORED
        COMMENT '검색용 정규화 제목'
        AFTER title;

-- 배포 후 확인: 공백 유무와 무관하게 같은 그룹이 잡히는지
-- SELECT name, search_name FROM `groups` WHERE search_name LIKE '%스트레이키즈%';
-- SELECT COUNT(*) FROM buncheols WHERE search_title LIKE '%아이브앨범%';

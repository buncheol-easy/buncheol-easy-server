-- 그룹 별칭(group_aliases) 시드 템플릿.
--
-- 테이블 자체는 schema.sql 의 CREATE TABLE IF NOT EXISTS 가 기동 시 만들어 준다 (spring.sql.init.mode=always).
-- 검색 정규화 컬럼 때와 달리 수동 ALTER 는 필요 없다 — 신규 테이블이라 IF NOT EXISTS 가 그대로 동작한다.
-- 이 파일은 "별칭 데이터를 어떤 모양으로 넣어야 하는가"를 고정하기 위한 템플릿이다.
--
-- ⚠ 실행 시 클라이언트 접속 charset 을 반드시 utf8mb4 로 지정한다:
--     mysql --default-character-set=utf8mb4 -u... DB < 이_파일.sql
--   기본값이 latin1 인 클라이언트로 한글을 INSERT 하면 alias 원문은 왕복 변환으로 멀쩡해 보이지만
--   생성 컬럼의 LOWER() 가 잘못된 내부 표현에 적용돼 search_alias 만 깨진다. 그러면 검색이 조용히 0건이 된다.
--
-- 제약 (위반하면 INSERT 가 실패한다 — 조용히 누락되지 않는다):
--   * 정규화 후 2자 이상.  "i-" 처럼 정규화하면 1자가 되는 별칭은 CHECK 에 걸린다.
--     1자 별칭은 부분일치 검색에서 거의 모든 검색어에 걸려 결과를 오염시킨다.
--   * 한 그룹 안에서 정규화 결과가 겹치면 안 된다. "i-dle" 과 "idle" 은 둘 다 "idle" 로 접히므로 하나만 넣는다.
--     그룹이 다르면 같은 별칭을 써도 된다 (여러 그룹이 정당하게 공유하는 표기가 있다).
--
-- 그룹은 id 가 아니라 이름으로 잡는다 — id 는 환경마다 달라 시드 SQL 을 staging/prod 로 옮길 수 없기 때문이다.
-- 대신 이름이 안 맞으면 그 행만 조용히 사라진다. 실제로 겪은 사례: 나무위키 표기는 "(여자)아이들" 이지만
-- DB 에 저장된 이름은 "아이들" 이라 두 행이 말없이 누락됐다. 그래서 스테이징 테이블에 먼저 넣고
-- **미매칭 목록을 반드시 출력**하게 만들었다. 마지막 SELECT 가 0건이 아니면 그 그룹명은 DB 표기와 다른 것이다.

CREATE TEMPORARY TABLE tmp_group_aliases (
    group_name VARCHAR(100) NOT NULL,
    alias      VARCHAR(100) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO tmp_group_aliases (group_name, alias) VALUES
    ('IVE',             '아이브'),
    ('프로미스나인',      'fromis_9'),
    ('프로미스나인',      '프미나'),
    ('아이들',           'idle'),
    ('아이들',           '여자애들'),
    ('ALPHA DRIVE ONE', '알파드라이브원'),
    ('투모로우바이투게더', 'TXT'),
    ('투모로우바이투게더', '투바투'),
    ('Stray Kids',      '스트레이키즈'),
    ('Stray Kids',      '스키즈');

INSERT INTO group_aliases (group_id, alias)
SELECT g.id, t.alias
FROM tmp_group_aliases t
JOIN `groups` g ON g.name = t.group_name
-- 재실행해도 안전하게. 이미 있는 별칭은 건너뛴다.
ON DUPLICATE KEY UPDATE alias = group_aliases.alias;

-- ⚠ 여기가 0건이어야 한다. 나오는 그룹명은 DB 표기와 달라 별칭이 통째로 누락된 것이다.
SELECT DISTINCT t.group_name AS 매칭_실패한_그룹명
FROM tmp_group_aliases t
LEFT JOIN `groups` g ON g.name = t.group_name
WHERE g.id IS NULL;

DROP TEMPORARY TABLE tmp_group_aliases;

-- 검증: 정규화가 제대로 됐는지 (한글이 깨졌으면 search_alias 에서 드러난다).
-- SELECT g.name, a.alias, a.search_alias FROM group_aliases a JOIN `groups` g ON g.id = a.group_id;

-- 검증: 실제로 교차 표기 검색이 걸리는지.
-- SELECT g.name FROM `groups` g
--  WHERE g.search_name LIKE '%아이브%'
--     OR EXISTS (SELECT 1 FROM group_aliases a WHERE a.group_id = g.id AND a.search_alias LIKE '%아이브%');

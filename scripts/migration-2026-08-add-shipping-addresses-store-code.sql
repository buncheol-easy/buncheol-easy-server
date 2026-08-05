-- feat/cvs-store-search (#94) 배포 전 prod 수동 실행 필수.
-- shipping_addresses 는 기존 테이블이라 schema.sql 의 CREATE TABLE IF NOT EXISTS 로는 컬럼이 생기지 않는다.
-- 누락 시 배송지 조회/등록/수정이 Unknown column 'store_code' 로 500 (앱은 정상 기동해 헬스체크로 안 잡힘).
ALTER TABLE shipping_addresses
    ADD COLUMN store_code VARCHAR(20) NULL
        COMMENT '선택한 접수처의 원천 점포 코드 (cvs_stores 재조인용, 자유입력 등록분은 NULL)'
        AFTER store_name;

-- 배포 후 확인: cvs_stores 적재 여부 (0건이면 buncheoleasy-crawler 의 load_cvs_stores.py 실행 필요)
-- SELECT COUNT(*) FROM cvs_stores;

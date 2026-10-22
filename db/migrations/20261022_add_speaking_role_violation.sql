-- 기존 DB에 새 서버 버전을 적용하기 전에 1회 실행한다.
-- 신규 DB는 CREATE_TABLE_SCRIPT.sql에 컬럼이 포함되어 있으므로 이 파일을 중복 실행하지 않는다.
-- 기존 재판의 역할 이탈 여부는 미평가 상태이므로 FALSE로 유지하고 점수는 변경하지 않는다.
ALTER TABLE speaking
    ADD COLUMN role_violation BOOLEAN NOT NULL DEFAULT FALSE COMMENT '명확한 역할 이탈 여부' AFTER novel;

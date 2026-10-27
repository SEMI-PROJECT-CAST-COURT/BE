-- 테이블 생성할 때 혹시 모를 문제 방지를 위해 모든 테이블 삭제
DROP TABLE IF EXISTS speaking;
DROP TABLE IF EXISTS trial;
DROP TABLE IF EXISTS penalty_tier;
DROP TABLE IF EXISTS evidence_card;
DROP TABLE IF EXISTS work_case;
DROP TABLE IF EXISTS work;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS account;

-- 테이블 생성
CREATE TABLE IF NOT EXISTS account (
    id INT AUTO_INCREMENT COMMENT '회원 고유 ID',
    username VARCHAR(255) NOT NULL COMMENT '유저 아이디',
    password VARCHAR(255) NOT NULL COMMENT '유저 비밀번호',
    authority VARCHAR(20) NOT NULL COMMENT '권한',
    nickname VARCHAR(20) NOT NULL COMMENT '닉네임',
    is_usage BOOLEAN NOT NULL DEFAULT TRUE COMMENT '삭제 여부',

    CONSTRAINT pk_account_id PRIMARY KEY ( id ),
    CONSTRAINT uq_user_name UNIQUE ( username ),
    CONSTRAINT ck_authority CHECK ( authority IN ( 'SUPER_ADMIN', 'ADMIN', 'USER' ))
) ENGINE=INNODB COMMENT '계정';

CREATE TABLE IF NOT EXISTS category (
    id INT AUTO_INCREMENT COMMENT '카테고리 고유 ID',
    name VARCHAR(10) NOT NULL COMMENT '카테고리명',
    sort_order INT NOT NULL COMMENT '카테고리 순서',

    CONSTRAINT pk_category_id PRIMARY KEY ( id ),
    CONSTRAINT uq_category_name UNIQUE ( name )
) ENGINE=INNODB COMMENT '카테고리';

CREATE TABLE IF NOT EXISTS work (
    id INT AUTO_INCREMENT COMMENT '작품 고유 ID',
    category_id INT NOT NULL COMMENT '카테고리 고유 ID',
    title VARCHAR(100) NOT NULL COMMENT '작품명',
    author VARCHAR(100) NOT NULL COMMENT '원작자',
    released_on DATE NOT NULL DEFAULT ( CURRENT_DATE ) COMMENT '공개일자',
    summary VARCHAR(200) COMMENT '작품 소개',
    poster VARCHAR(255) NOT NULL COMMENT '포스터 이미지',
    is_usage BOOLEAN NOT NULL DEFAULT TRUE COMMENT '삭제 여부',

    CONSTRAINT pk_work_id PRIMARY KEY ( id ),
    CONSTRAINT fk_category_id FOREIGN KEY ( category_id ) REFERENCES category ( id ),
    CONSTRAINT uq_work_category_title UNIQUE ( category_id, ( IF( is_usage, title, NULL ) ) )
) ENGINE=INNODB COMMENT '작품';

CREATE TABLE IF NOT EXISTS work_case (
    id INT AUTO_INCREMENT COMMENT '사건 고유 ID',
    work_id INT NOT NULL COMMENT '작품 고유 ID',
    question VARCHAR(100) NOT NULL COMMENT '쟁점명',
    suspect VARCHAR(50) NOT NULL COMMENT '피의자',
    victim VARCHAR(50) NOT NULL COMMENT '피해자',
    summary VARCHAR(1000) NOT NULL COMMENT '사건 개요',
    progress VARCHAR(1000) NOT NULL COMMENT '사건 경과',
    is_usage BOOLEAN NOT NULL DEFAULT TRUE COMMENT '삭제 여부',

    CONSTRAINT pk_work_case_id PRIMARY KEY ( id ),
    CONSTRAINT fk_work_id FOREIGN KEY ( work_id ) REFERENCES work ( id ),
    CONSTRAINT uq_work_case_work_question UNIQUE ( work_id, ( IF( is_usage, question, NULL ) ) )
) ENGINE=INNODB COMMENT '사건';

CREATE TABLE IF NOT EXISTS evidence_card (
    id INT AUTO_INCREMENT COMMENT '근거 카드 고유 ID',
    work_case_id INT NOT NULL COMMENT '사건 고유 ID',
    side VARCHAR(12) NOT NULL COMMENT '근거 카드 사용 역할',
    title VARCHAR(40) NOT NULL COMMENT '근거 카드 제목',
    body VARCHAR(150) NOT NULL COMMENT '근거 카드 내용',
    weight INT NOT NULL COMMENT '가중치',

    CONSTRAINT pk_evidence_card_id PRIMARY KEY ( id ),
    CONSTRAINT fk_evidence_card_work_case_id FOREIGN KEY ( work_case_id ) REFERENCES work_case ( id ),
    CONSTRAINT ck_evidence_card_side CHECK ( side IN ( 'PROSECUTION', 'DEFENSE' ) ),
    CONSTRAINT ck_weight CHECK ( weight BETWEEN 1 AND 20 )
) ENGINE=INNODB COMMENT '근거 카드';

CREATE TABLE IF NOT EXISTS penalty_tier (
    id INT AUTO_INCREMENT COMMENT '형벌표 고유 ID',
    work_case_id INT NOT NULL COMMENT '사건 고유 ID',
    tier INT NOT NULL COMMENT '형벌 단계',
    sentence VARCHAR(100) NOT NULL COMMENT '형벌명',

    CONSTRAINT pk_penalty_tier PRIMARY KEY ( id ),
    CONSTRAINT fk_penalty_tier_work_case_id FOREIGN KEY ( work_case_id ) REFERENCES work_case ( id ),
    CONSTRAINT uq_penalty_tier_case_tier UNIQUE ( work_case_id, tier ),
    CONSTRAINT ck_tier CHECK ( tier BETWEEN 1 AND 6 )
) ENGINE=INNODB COMMENT '형벌표';

CREATE TABLE IF NOT EXISTS trial (
    id INT AUTO_INCREMENT COMMENT '재판 고유 ID',
    work_case_id INT NOT NULL COMMENT '사건 고유 ID',
    user_id INT NOT NULL COMMENT '유저 고유 ID',
    role VARCHAR(12) NOT NULL COMMENT '재판 참여 역할',
    mode VARCHAR(12) NOT NULL COMMENT '재판 모드',
    card_ids JSON NOT NULL COMMENT '근거 카드 리스트',
    score INT NOT NULL DEFAULT 0 COMMENT '최종 게이지',
    penalty_tier INT NOT NULL COMMENT '형벌 단계',
    judge_summary VARCHAR(1000) NOT NULL COMMENT '판사 총평',
    case_snapshot JSON NOT NULL COMMENT '사건 내용',

    CONSTRAINT pk_trial_id PRIMARY KEY ( id ),
    CONSTRAINT fk_trial_work_case_id FOREIGN KEY ( work_case_id ) REFERENCES work_case ( id ),
    CONSTRAINT fk_account_id FOREIGN KEY ( user_id ) REFERENCES account ( id ),
    CONSTRAINT uq_trial_user_case_role UNIQUE ( user_id, work_case_id, role ),
    CONSTRAINT ck_role CHECK ( role IN ( 'PROSECUTION', 'DEFENSE', 'JUDGE' ) ),
    CONSTRAINT ck_mode CHECK ( mode IN ( 'CHICKEN', 'CHALLENGER', 'IRON' ) ),
    CONSTRAINT ck_score CHECK ( score BETWEEN 0 AND 100 ),
    CONSTRAINT ck_penalty_tier CHECK ( penalty_tier BETWEEN 1 AND 6 )
) ENGINE=INNODB COMMENT '재판';

CREATE TABLE IF NOT EXISTS speaking (
    id INT AUTO_INCREMENT COMMENT '발언 고유 ID',
    trial_id INT NOT NULL COMMENT '재판 고유 ID',
    seq INT NOT NULL COMMENT '발언 순서',
    stage VARCHAR(12) NOT NULL COMMENT '재판 진행 단계',
    side VARCHAR(12) NOT NULL COMMENT '발언 역할',
    speaker VARCHAR(6) NOT NULL COMMENT '발언 주체',
    content VARCHAR(1000) NOT NULL COMMENT '발언 내용',
    issue BOOLEAN NOT NULL DEFAULT FALSE COMMENT '쟁점 부합 판단',
    evidence BOOLEAN NOT NULL DEFAULT FALSE COMMENT '근거 부합 판단',
    rebut BOOLEAN COMMENT '반박 부합 판단',
    novel BOOLEAN NOT NULL DEFAULT FALSE COMMENT '새로움 부합 판단',
    grade VARCHAR(8) NOT NULL DEFAULT 'OFF' COMMENT '발언 등급',
    delta INT NOT NULL DEFAULT 0 COMMENT '게이지 변화량',
    score_after INT NOT NULL DEFAULT 0 COMMENT '총 게이지',

    CONSTRAINT pk_speaking_id PRIMARY KEY ( id ),
    CONSTRAINT fk_trial_id FOREIGN KEY ( trial_id ) REFERENCES trial ( id ),
    CONSTRAINT uq_speaking_trial_seq UNIQUE ( trial_id, seq ),
    CONSTRAINT ck_stage CHECK ( stage IN ( 'OPENING', 'CARD', 'CLOSING' ) ),
    CONSTRAINT ck_speaking_side CHECK ( side IN ( 'PROSECUTION', 'DEFENSE', 'JUDGE' ) ),
    CONSTRAINT ck_speaker CHECK ( speaker IN ( 'USER', 'AI' ) ),
    CONSTRAINT ck_grade CHECK ( grade IN ( 'STRONG', 'OK', 'WEAK', 'OFF' ) ),
    CONSTRAINT ck_score_after CHECK ( score_after BETWEEN 0 AND 100 )
) ENGINE=INNODB COMMENT '발언';

-- COMMIT이 안됨을 방지하기 위해 COMMIT 쿼리 실행
COMMIT;
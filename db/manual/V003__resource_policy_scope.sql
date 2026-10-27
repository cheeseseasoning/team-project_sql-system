-- 리소스 조회·반출 정책을 객체 단위로 관리한다.
-- DATA_ACCESS_POLICY는 정책 값이 아닌 리소스별 접근 부서 목록으로 사용한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'DATA_RESOURCE' AND COLUMN_NAME = 'ACCESS_LEVEL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE DATA_RESOURCE ADD (ACCESS_LEVEL VARCHAR2(20) DEFAULT ''GENERAL'' NOT NULL)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'DATA_RESOURCE' AND COLUMN_NAME = 'EXPORT_POLICY';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE DATA_RESOURCE ADD (EXPORT_POLICY VARCHAR2(20) DEFAULT ''BLOCK'' NOT NULL)';
    END IF;

    -- 기존 부서 정책 테이블에 해당 열이 있을 때만 이전 값을 객체 정책으로 통합한다.
    -- 열이 없는 스키마는 리소스 열 추가 시 적용한 GENERAL 기본값을 유지한다.
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'DATA_ACCESS_POLICY' AND COLUMN_NAME = 'ACCESS_LEVEL';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE q'[
            UPDATE DATA_RESOURCE R
               SET ACCESS_LEVEL = NVL((
                       SELECT CASE WHEN MAX(CASE WHEN P.ACCESS_LEVEL = 'SENSITIVE' THEN 1 ELSE 0 END) = 1
                                   THEN 'SENSITIVE' ELSE 'GENERAL' END
                         FROM DATA_ACCESS_POLICY P WHERE P.RESOURCE_ID = R.RESOURCE_ID
                   ), 'GENERAL')
        ]';
    END IF;

    -- 부서 정책의 EXPORT_POLICY 열이 있으면 보수적으로 통합하고, 없으면 BLOCK 기본값을 유지한다.
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'DATA_ACCESS_POLICY' AND COLUMN_NAME = 'EXPORT_POLICY';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE q'[
            UPDATE DATA_RESOURCE R
               SET EXPORT_POLICY = NVL((
                       SELECT CASE WHEN COUNT(*) = 0 THEN 'BLOCK'
                                   WHEN MAX(CASE WHEN P.EXPORT_POLICY = 'BLOCK' THEN 1 ELSE 0 END) = 1 THEN 'BLOCK'
                                   ELSE 'ALLOW' END
                         FROM DATA_ACCESS_POLICY P WHERE P.RESOURCE_ID = R.RESOURCE_ID
                   ), 'BLOCK')
        ]';
    END IF;
    COMMIT;
END;

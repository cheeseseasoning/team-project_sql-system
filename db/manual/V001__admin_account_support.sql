-- 계정 관리 기능용 Oracle DB 변경 스크립트
-- DBeaver에서 실행할 때는 SQL 스크립트 실행(Alt+X)을 사용하세요.
DECLARE
    v_start NUMBER;
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM USER_SEQUENCES
    WHERE SEQUENCE_NAME = 'SEQ_USERS';

    IF v_count = 0 THEN
        SELECT NVL(MAX(USER_ID), 0) + 1
        INTO v_start
        FROM USERS;

        EXECUTE IMMEDIATE
            'CREATE SEQUENCE SEQ_USERS START WITH ' || v_start ||
            ' INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*)
    INTO v_count
    FROM USER_INDEXES
    WHERE INDEX_NAME = 'UX_USERS_LOGIN_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE UNIQUE INDEX UX_USERS_LOGIN_ID ON USERS (LOGIN_ID)';
    END IF;
END;
/

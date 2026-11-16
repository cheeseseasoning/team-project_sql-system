-- 부서 공동 결재 지원
-- Java·Mapper 수정 완료 후 애플리케이션을 중지하고 적용한다.
-- SQLFLOW 또는 해당 테이블 변경·참조 권한이 있는 계정으로 실행한다.
-- Oracle DDL은 자동 커밋되므로 전체 블록이 하나의 트랜잭션으로 롤백되지는 않는다.

DECLARE
    V_COUNT NUMBER;

    PROCEDURE ADD_COLUMN_IF_MISSING(
        P_COLUMN_NAME VARCHAR2,
        P_DEFINITION VARCHAR2
    ) IS
        V_EXISTS NUMBER;
    BEGIN
        SELECT COUNT(*) INTO V_EXISTS
        FROM ALL_TAB_COLUMNS
        WHERE OWNER = 'SQLFLOW'
          AND TABLE_NAME = 'APPROVAL'
          AND COLUMN_NAME = P_COLUMN_NAME;

        IF V_EXISTS = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE SQLFLOW.APPROVAL ADD (' || P_DEFINITION || ')';
        END IF;
    END;

    PROCEDURE ADD_CONSTRAINT_IF_MISSING(
        P_CONSTRAINT_NAME VARCHAR2,
        P_DEFINITION VARCHAR2
    ) IS
        V_EXISTS NUMBER;
        V_COLLISION NUMBER;
    BEGIN
        SELECT COUNT(*) INTO V_COLLISION
        FROM ALL_CONSTRAINTS
        WHERE OWNER = 'SQLFLOW'
          AND CONSTRAINT_NAME = P_CONSTRAINT_NAME
          AND TABLE_NAME <> 'APPROVAL';

        IF V_COLLISION > 0 THEN
            RAISE_APPLICATION_ERROR(
                -20013,
                '다른 테이블에서 사용 중인 제약조건 이름입니다: ' || P_CONSTRAINT_NAME
            );
        END IF;

        SELECT COUNT(*) INTO V_EXISTS
        FROM ALL_CONSTRAINTS
        WHERE OWNER = 'SQLFLOW'
          AND TABLE_NAME = 'APPROVAL'
          AND CONSTRAINT_NAME = P_CONSTRAINT_NAME;

        IF V_EXISTS = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE SQLFLOW.APPROVAL ADD CONSTRAINT '
                || P_CONSTRAINT_NAME || ' ' || P_DEFINITION;
        ELSE
            EXECUTE IMMEDIATE
                'ALTER TABLE SQLFLOW.APPROVAL ENABLE VALIDATE CONSTRAINT '
                || P_CONSTRAINT_NAME;
        END IF;
    END;

BEGIN
    -- 이전 버전 V013이 적용됐다면 자동으로 컬럼·제약을 제거하지 않고 중단한다.
    SELECT COUNT(*) INTO V_COUNT
    FROM ALL_TAB_COLUMNS
    WHERE OWNER = 'SQLFLOW'
      AND TABLE_NAME = 'APPROVAL'
      AND COLUMN_NAME = 'DECIDED_BY';

    IF V_COUNT > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20015,
            '이전 V013의 DECIDED_BY 컬럼이 있습니다. 적용 상태를 확인한 뒤 진행하세요.'
        );
    END IF;

    -- 기존 결재는 모두 개인 지정(USER)으로 유지한다.
    ADD_COLUMN_IF_MISSING(
        'TARGET_TYPE',
        q'[TARGET_TYPE VARCHAR2(20 CHAR) DEFAULT 'USER' NOT NULL]'
    );

    -- 부서 결재의 담당 부서. 처리 후에도 유지한다.
    ADD_COLUMN_IF_MISSING(
        'APPROVER_DEPT_ID',
        'APPROVER_DEPT_ID NUMBER(19)'
    );

    ADD_CONSTRAINT_IF_MISSING(
        'FK_APPROVAL_TARGET_DEPT',
        'FOREIGN KEY (APPROVER_DEPT_ID) REFERENCES SQLFLOW.DEPARTMENT (DEPT_ID)'
    );

    -- 개인 지정: APPROVER_ID는 지정된 결재자이며 항상 필수다.
    -- 부서 지정: 처리 전에는 비어 있고, 승인·반려 시 실제 처리자를 저장한다.
    ADD_CONSTRAINT_IF_MISSING(
        'CK_APPROVAL_TARGET',
        q'[
            CHECK (
                (
                    TARGET_TYPE = 'USER'
                    AND APPROVER_ID IS NOT NULL
                    AND APPROVER_DEPT_ID IS NULL
                )
                OR
                (
                    TARGET_TYPE = 'DEPARTMENT'
                    AND APPROVER_DEPT_ID IS NOT NULL
                    AND (
                        (
                            STATUS IN ('WAITING', 'PENDING', 'CANCELLED')
                            AND APPROVER_ID IS NULL
                        )
                        OR
                        (
                            STATUS IN ('APPROVED', 'REJECTED')
                            AND APPROVER_ID IS NOT NULL
                        )
                    )
                )
            )
        ]'
    );

    -- 개인 지정의 필수 조건은 CK_APPROVAL_TARGET으로 유지한다.
    SELECT COUNT(*) INTO V_COUNT
    FROM ALL_TAB_COLUMNS
    WHERE OWNER = 'SQLFLOW'
      AND TABLE_NAME = 'APPROVAL'
      AND COLUMN_NAME = 'APPROVER_ID'
      AND NULLABLE = 'N';

    IF V_COUNT = 1 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE SQLFLOW.APPROVAL MODIFY (APPROVER_ID NULL)';
    END IF;

    -- 기존 개인 결재 대기 인덱스는 유지한다.
    SELECT COUNT(*) INTO V_COUNT
    FROM ALL_INDEXES
    WHERE OWNER = 'SQLFLOW'
      AND INDEX_NAME = 'IX_APPROVAL_DEPT_INBOX'
      AND TABLE_NAME <> 'APPROVAL';

    IF V_COUNT > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20014,
            'IX_APPROVAL_DEPT_INBOX 이름이 다른 테이블에서 사용 중입니다.'
        );
    END IF;

    SELECT COUNT(*) INTO V_COUNT
    FROM ALL_INDEXES
    WHERE OWNER = 'SQLFLOW'
      AND TABLE_NAME = 'APPROVAL'
      AND INDEX_NAME = 'IX_APPROVAL_DEPT_INBOX';

    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE INDEX SQLFLOW.IX_APPROVAL_DEPT_INBOX
            ON SQLFLOW.APPROVAL (APPROVER_DEPT_ID, STATUS)
        ]';
    END IF;

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/

-- 적용 후 컬럼 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, NULLABLE, DATA_DEFAULT
FROM ALL_TAB_COLUMNS
WHERE OWNER = 'SQLFLOW'
  AND TABLE_NAME = 'APPROVAL'
  AND COLUMN_NAME IN (
      'TARGET_TYPE', 'APPROVER_ID', 'APPROVER_DEPT_ID'
  )
ORDER BY COLUMN_ID;

-- 적용 후 새 제약조건 확인
SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE, STATUS, VALIDATED
FROM ALL_CONSTRAINTS
WHERE OWNER = 'SQLFLOW'
  AND TABLE_NAME = 'APPROVAL'
  AND CONSTRAINT_NAME IN (
      'FK_APPROVAL_TARGET_DEPT',
      'CK_APPROVAL_TARGET'
  )
ORDER BY CONSTRAINT_NAME;

-- 기존 결재 데이터 보존 확인
SELECT TARGET_TYPE, STATUS, COUNT(*) AS TOTAL_COUNT
FROM SQLFLOW.APPROVAL
GROUP BY TARGET_TYPE, STATUS
ORDER BY TARGET_TYPE, STATUS;
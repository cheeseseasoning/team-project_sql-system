/* 역할 코드는 권한 판정에 유지하고, 모든 계정 화면은 이 표시명과 설명을 공유한다. */
window.AccountRoles = (() => {
    const details = Object.freeze({
        SYSTEM_ADMIN: Object.freeze({ label: '시스템 관리', description: '계정 생성·수정 및 역할 부여' }),
        DB_ADMIN: Object.freeze({ label: 'DB 운영 관리', description: 'DB 변경 결과 등록·정정 및 데이터 접근·반출 정책 관리' }),
        DB_APPROVER: Object.freeze({ label: '조회 SQL 실행', description: '최종 승인된 SELECT SQL 실행' }),
        SENSITIVE_DATA_READER: Object.freeze({ label: '민감 데이터 조회', description: '부서 접근 정책에서 허용된 민감 데이터 조회' })
    });
    const labels = Object.freeze(Object.fromEntries(Object.entries(details).map(([code, role]) => [code, role.label])));
    return Object.freeze({ details, labels, label: code => labels[code] || code });
})();

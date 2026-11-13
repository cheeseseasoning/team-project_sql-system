/* 화면 표시 전용: 정렬한 문자열을 저장·실행 요청에 사용하지 않는다. */
window.SqlDisplay = Object.freeze({
    format(sql) {
        const original = sql == null ? '' : String(sql);
        if (!original.trim() || !window.sqlFormatter) return original;

        try {
            return window.sqlFormatter.format(original, {
                language: 'mysql',
                tabWidth: 4,
                keywordCase: 'upper'
            });
        } catch (error) {
            // 미지원 구문도 상세 화면을 열 수 있도록 원문을 그대로 보여준다.
            return original;
        }
    }
});

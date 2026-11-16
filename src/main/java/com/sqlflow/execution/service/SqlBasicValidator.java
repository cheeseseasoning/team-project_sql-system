package com.sqlflow.execution.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/** DB 문법을 대신 판단하지 않고 유형·문장 경계·마지막 세미콜론만 확인한다. */
@Component
public class SqlBasicValidator {

    private static final Set<String> COMMANDS = Set.of("SELECT", "CREATE", "INSERT", "UPDATE", "DELETE");

    public Result validate(String type, String sqlText) {
        if (sqlText == null || sqlText.isBlank()) return blocked("SQL을 입력하세요.");
        String sql = sqlText.strip();
        if (!sql.endsWith(";")) return blocked("SQL의 마지막은 세미콜론(;)으로 끝나야 합니다.");
        List<Token> tokens;
        try {
            tokens = tokenize(sql);
        } catch (IllegalArgumentException exception) {
            return blocked(exception.getMessage());
        }
        if (tokens.isEmpty()) return blocked("SQL을 입력하세요.");
        Token last = tokens.get(tokens.size() - 1);
        if (!";".equals(last.text()) || last.end() != sql.length()) {
            return blocked("마지막 세미콜론 뒤에는 주석 없이 공백만 입력하세요.");
        }
        String command = tokens.get(0).text();
        // CTE 본문 안의 SELECT가 아니라 괄호 밖의 실제 명령을 찾는다.
        if ("WITH".equals(command)) {
            int depth = 0;
            command = "";
            for (int i = 1; i < tokens.size(); i++) {
                String token = tokens.get(i).text();
                if ("(".equals(token)) depth++;
                else if (")".equals(token)) depth--;
                else if (depth == 0 && COMMANDS.contains(token)) {
                    command = token;
                    break;
                }
            }
        }
        if (!type.equals(command)) {
            if ("SELECT".equals(type)) {
                return blocked("EXPLAIN".equals(command)
                        ? "조회 요청에는 SELECT 문만 입력하세요. EXPLAIN은 검증 시 자동으로 적용되므로 EXPLAIN 또는 EXPLAIN ANALYZE를 제거하세요."
                        : "조회 요청에는 SELECT 문만 입력할 수 있습니다. 데이터 변경 SQL은 데이터 변경 요청에서 작성하세요.");
            }
            return blocked("선택한 유형은 " + type + "입니다. " + type + " 문을 입력하거나 SQL에 맞게 유형을 변경하세요.");
        }

        // 조회 실행 경로의 기존 읽기 전용 제한은 문법 완화와 별개로 유지한다.
        if ("SELECT".equals(type)) {
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i).text();
                String next = i + 1 < tokens.size() ? tokens.get(i + 1).text() : "";
                if ("INTO".equals(token) || ("FOR".equals(token) && Set.of("UPDATE", "SHARE").contains(next))
                        || ("LOCK".equals(token) && "IN".equals(next)) || (":".equals(token) && "=".equals(next))) {
                    return blocked("조회 요청에서는 파일 출력·변수 대입·행 잠금 구문을 사용할 수 없습니다.");
                }
            }
        }
        boolean routine = "CREATE".equals(type) && tokens.stream()
                .takeWhile(token -> !";".equals(token.text()))
                .anyMatch(token -> Set.of("PROCEDURE", "FUNCTION", "TRIGGER", "EVENT").contains(token.text()));
        int blocks = 0;
        int cases = 0;
        int controls = 0;
        boolean uncertain = false;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).text();
            String previous = i == 0 ? "" : tokens.get(i - 1).text();
            String next = i + 1 == tokens.size() ? "" : tokens.get(i + 1).text();
            if (routine) {
                if ("BEGIN".equals(token)) blocks++;
                if (!"END".equals(previous) && ("LOOP".equals(token) || "WHILE".equals(token)
                        || ("REPEAT".equals(token) && !"(".equals(next))
                        || ("IF".equals(token) && isControlIf(tokens, i)))) controls++;
                if ("CASE".equals(token) && !"END".equals(previous)) cases++;
                if ("END".equals(token)) {
                    if ("CASE".equals(next) || (cases > 0 && !Set.of("IF", "LOOP", "WHILE", "REPEAT").contains(next))) cases--;
                    // END IF/LOOP/WHILE/REPEAT는 BEGIN 블록이 아닌 제어문을 닫는다.
                    else if (Set.of("IF", "LOOP", "WHILE", "REPEAT").contains(next)) controls--;
                    else blocks--;
                    if (blocks < 0 || cases < 0 || controls < 0) uncertain = true;
                }
            }
            if (";".equals(token) && (!routine || (blocks <= 0 && cases <= 0 && controls <= 0)) && i != tokens.size() - 1) {
                return blocked("SQL 항목마다 한 문장만 입력하세요.");
            }
        }
        if (routine && (blocks != 0 || cases != 0 || controls != 0 || uncertain)) {
            return new Result(false, "복합 CREATE 문장의 경계를 확정하지 못했습니다. DB팀 검토가 필요합니다.");
        }
        return new Result(false, null);
    }

    /** IF 함수의 괄호와 IF (...) THEN 제어문을 구별한다. */
    private boolean isControlIf(List<Token> tokens, int index) {
        // CREATE ... IF NOT EXISTS는 본문의 IF 제어문이 아니다.
        if (index > 0 && Set.of("PROCEDURE", "FUNCTION", "TRIGGER", "EVENT").contains(tokens.get(index - 1).text())) return false;
        if (index + 1 >= tokens.size() || !"(".equals(tokens.get(index + 1).text())) return true;
        int depth = 0;
        for (int i = index + 1; i < tokens.size(); i++) {
            String token = tokens.get(i).text();
            if ("(".equals(token)) depth++;
            if (")".equals(token) && --depth == 0) {
                return i + 1 < tokens.size() && "THEN".equals(tokens.get(i + 1).text());
            }
        }
        return false;
    }

    /** 문자열·식별자·일반 주석의 세미콜론은 문장 경계로 취급하지 않는다. */
    private List<Token> tokenize(String sql) {
        List<Token> result = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char ch = sql.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (ch == '#' || (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-'
                    && (i + 2 == sql.length() || Character.isWhitespace(sql.charAt(i + 2))))) {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                continue;
            }
            if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                // MySQL 실행 주석은 실제 명령을 숨길 수 있어 일반 주석으로 무시하지 않는다.
                if (i + 2 < sql.length() && sql.charAt(i + 2) == '!') {
                    throw new IllegalArgumentException("실행 주석 대신 실행할 SQL을 직접 입력하세요.");
                }
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("닫히지 않은 SQL 주석이 있습니다.");
                i = end + 2;
                continue;
            }
            if (ch == 39 || ch == 34 || ch == 96) {
                char quote = ch;
                boolean closed = false;
                i++;
                while (i < sql.length()) {
                    char current = sql.charAt(i++);
                    if (current == 92 && quote != 96 && i < sql.length()) { i++; continue; }
                    if (current == quote) {
                        if (i < sql.length() && sql.charAt(i) == quote) { i++; continue; }
                        closed = true;
                        break;
                    }
                }
                if (!closed) throw new IllegalArgumentException("닫히지 않은 SQL 문자열 또는 식별자가 있습니다.");
                result.add(new Token("<QUOTED>", i));
                continue;
            }
            int start = i++;
            if (Character.isLetter(ch) || ch == '_') {
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$')) i++;
            }
            result.add(new Token(sql.substring(start, i).toUpperCase(Locale.ROOT), i));
        }
        return result;
    }

    private Result blocked(String message) { return new Result(true, message); }
    private record Token(String text, int end) { }
    public record Result(boolean blocked, String warning) { }
}

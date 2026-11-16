package com.sqlflow.request.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 문법 검증과 별개로 CREATE 헤더의 대상 종류·이름만 판별한다. 분석 실패는 OTHER로 간주하지 않는다. */
public final class CreateSqlTargetResolver {
    private CreateSqlTargetResolver() { }
    private static final Set<String> OTHER_TYPES = Set.of("TABLE", "INDEX", "DATABASE", "SCHEMA", "USER", "ROLE",
            "PROCEDURE", "FUNCTION", "TRIGGER", "EVENT", "TABLESPACE", "SERVER", "LOGFILE", "RESOURCE", "UNDO");

    public static Target resolve(String sql) {
        try {
            Cursor tokens = new Cursor(tokenize(sql));
            if (!tokens.accept("CREATE")) return unknown();
            if (tokens.accept("OR") && !tokens.accept("REPLACE")) return unknown();
            if (tokens.accept("ALGORITHM")) {
                if (!tokens.accept("=") || !Set.of("UNDEFINED", "MERGE", "TEMPTABLE").contains(tokens.word())) return unknown();
            }
            if (tokens.accept("DEFINER")) {
                tokens.accept("=");
                String account = tokens.identifier();
                if (account == null) return unknown();
                if (tokens.accept("(")) {
                    if (!"CURRENT_USER".equalsIgnoreCase(account) || !tokens.accept(")")) return unknown();
                } else if (tokens.accept("@") && tokens.identifier() == null) return unknown();
            }
            if (tokens.accept("SQL") && (!tokens.accept("SECURITY") || !Set.of("DEFINER", "INVOKER").contains(tokens.word()))) return unknown();
            if (tokens.accept("VIEW")) {
                String first = tokens.identifier();
                if (first == null) return unknown();
                String schema = null;
                String view = first;
                if (tokens.accept(".")) {
                    schema = first;
                    view = tokens.identifier();
                    if (view == null) return unknown();
                }
                // 본문에 VIEW라는 단어가 포함된 것만으로 생성 대상으로 인정하지 않는다.
                if (!tokens.peek("AS") && !tokens.peek("(")) return unknown();
                return new Target(Kind.VIEW, schema, view);
            }
            tokens.accept("TEMPORARY");
            if (tokens.accept("SPATIAL")) {
                return tokens.accept("INDEX") || tokens.accept("REFERENCE") ? new Target(Kind.OTHER, null, null) : unknown();
            }
            if (tokens.accept("UNIQUE") || tokens.accept("FULLTEXT")) {
                if (tokens.accept("INDEX")) return new Target(Kind.OTHER, null, null);
                return unknown();
            }
            return OTHER_TYPES.contains(tokens.word()) ? new Target(Kind.OTHER, null, null) : unknown();
        } catch (IllegalArgumentException exception) {
            return unknown();
        }
    }

    private static Target unknown() { return new Target(Kind.UNKNOWN, null, null); }

    /** 주석과 따옴표 안의 키워드를 헤더 명령으로 오인하지 않도록 분리한다. */
    private static List<Token> tokenize(String sql) {
        if (sql == null) throw new IllegalArgumentException();
        List<Token> result = new ArrayList<>();
        int i = 0;
        boolean viewHeader = false;
        // 헤더 판별에 충분한 토큰만 읽어 SQL 본문 크기에 따라 메모리가 늘지 않게 한다.
        while (i < sql.length() && result.size() < 80) {
            char ch = sql.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (ch == '#' || (ch == '-' && i + 2 < sql.length() && sql.charAt(i + 1) == '-'
                    && Character.isWhitespace(sql.charAt(i + 2)))) {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                continue;
            }
            if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                if (i + 2 < sql.length() && sql.charAt(i + 2) == '!') throw new IllegalArgumentException();
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException();
                i = end + 2;
                continue;
            }
            if (ch == 39 || ch == 34 || ch == 96) {
                char quote = ch;
                StringBuilder value = new StringBuilder();
                boolean closed = false;
                i++;
                while (i < sql.length()) {
                    char current = sql.charAt(i++);
                    if (current == 92 && quote != 96 && i < sql.length()) { value.append(sql.charAt(i++)); continue; }
                    if (current == quote) {
                        if (i < sql.length() && sql.charAt(i) == quote) { value.append(quote); i++; continue; }
                        closed = true;
                        break;
                    }
                    value.append(current);
                }
                if (!closed) throw new IllegalArgumentException();
                result.add(new Token(value.toString(), true));
                continue;
            }
            int start = i++;
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') {
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$')) i++;
            }
            String text = sql.substring(start, i);
            result.add(new Token(text, false));
            if ("VIEW".equalsIgnoreCase(text)) viewHeader = true;
            if (viewHeader && "AS".equalsIgnoreCase(text)) break;
        }
        return result;
    }

    private record Token(String text, boolean quoted) { }
    private static final class Cursor {
        private final List<Token> tokens;
        private int index;
        Cursor(List<Token> tokens) { this.tokens = tokens; }
        boolean peek(String word) {
            return index < tokens.size() && !tokens.get(index).quoted() && word.equalsIgnoreCase(tokens.get(index).text());
        }
        boolean accept(String word) { if (!peek(word)) return false; index++; return true; }
        String word() {
            if (index >= tokens.size() || tokens.get(index).quoted()) return "";
            return tokens.get(index++).text().toUpperCase(java.util.Locale.ROOT);
        }
        String identifier() {
            if (index >= tokens.size()) return null;
            Token token = tokens.get(index++);
            return !token.text().isBlank() && (token.quoted() || token.text().matches("[\\p{L}\\p{N}_$]+")) ? token.text() : null;
        }
    }
    public enum Kind { VIEW, OTHER, UNKNOWN }
    public record Target(Kind kind, String schema, String view) { }
}

package com.sqlflow.execution.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.SqlColumnResolver.Scope;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.OldOracleJoinBinaryExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;

/**
 * ON·WHERE의 기본 조건 구조와 참조 컬럼을 검사한다.
 * 함수·서브쿼리 등 미지원 표현식은 건너뛰지 않고 차단한다.
 */
@Component
@RequiredArgsConstructor
public class SqlConditionValidator {

    private static final int MAX_DEPTH = 64;

    private static final Set<Class<?>> COMPARISONS = Set.of(
            EqualsTo.class,
            NotEqualsTo.class,
            GreaterThan.class,
            GreaterThanEquals.class,
            MinorThan.class,
            MinorThanEquals.class);

    private final SqlColumnResolver columnResolver;

    /**
     * 조건에 사용된 실제 컬럼을 반환한다.
     * null은 WHERE 등 해당 조건절이 없는 경우에만 사용한다.
     */
    public Set<ResolvedColumn> validate(Expression condition, Scope scope) {

        if (scope == null) {
            throw new IllegalArgumentException("컬럼 검사 범위가 필요합니다.");
        }

        if (condition == null) {
            return Set.of();
        }

        Set<ResolvedColumn> references = new LinkedHashSet<>();
        validatePredicate(condition, scope, references, 0);

        return Set.copyOf(references);
    }

    /** 조건식의 논리 구조를 따라 모든 분기를 검사한다. */
    private void validatePredicate(Expression expression, Scope scope,
            Set<ResolvedColumn> references, int depth) {

        checkDepth(depth);

        if (expression == null) {
            throw badRequest("조건식 일부가 비어 있습니다.");
        }

        if (expression instanceof ParenthesedExpressionList<?> parentheses) {
            requireSingleExpression(parentheses);
            validatePredicate(parentheses.get(0), scope, references, depth + 1);
            return;
        }

        if (expression instanceof AndExpression and) {
            if (and.isUseOperator()) {
                throw badRequest("AND 연산자의 대체 표기는 지원하지 않습니다.");
            }

            validatePredicate(and.getLeftExpression(), scope, references, depth + 1);
            validatePredicate(and.getRightExpression(), scope, references, depth + 1);
            return;
        }

        if (expression instanceof OrExpression or) {
            validatePredicate(or.getLeftExpression(), scope, references, depth + 1);
            validatePredicate(or.getRightExpression(), scope, references, depth + 1);
            return;
        }

        if (expression instanceof NotExpression not) {
            validatePredicate(not.getExpression(), scope, references, depth + 1);
            return;
        }

        if (COMPARISONS.contains(expression.getClass())) {
            BinaryExpression comparison = (BinaryExpression) expression;

            // 구식 외부 JOIN과 계층 조회는 별도 해석이 필요하므로 차단한다.
            if (comparison instanceof OldOracleJoinBinaryExpression oracle
                    && (oracle.getOldOracleJoinSyntax() != 0
                    || oracle.getOraclePriorPosition() != 0)) {
                throw badRequest("Oracle의 (+)·PRIOR 조건은 지원하지 않습니다.");
            }

            validateValue(comparison.getLeftExpression(), scope, references, depth + 1);
            validateValue(comparison.getRightExpression(), scope, references, depth + 1);
            return;
        }

        if (expression instanceof IsNullExpression isNull) {
            validateValue(isNull.getLeftExpression(), scope, references, depth + 1);
            return;
        }

        throw badRequest("지원하지 않는 조건식입니다: " + expression);
    }

    /** 비교식의 양쪽 값에 허용한 컬럼·리터럴만 있는지 검사한다. */
    private void validateValue(Expression expression, Scope scope,
            Set<ResolvedColumn> references, int depth) {

        checkDepth(depth);

        if (expression == null) {
            throw badRequest("비교할 값이 없습니다.");
        }

        if (expression instanceof Column column) {
            references.add(columnResolver.resolveColumn(column, scope));
            return;
        }

        if (expression instanceof LongValue
                || expression instanceof DoubleValue
                || expression instanceof StringValue
                || expression instanceof NullValue) {
            return;
        }

        if (expression instanceof ParenthesedExpressionList<?> parentheses) {
            requireSingleExpression(parentheses);
            validateValue(parentheses.get(0), scope, references, depth + 1);
            return;
        }

        if (expression instanceof SignedExpression signed) {
            Expression value = signed.getExpression();

            if ((signed.getSign() == '+' || signed.getSign() == '-')
                    && (value instanceof LongValue || value instanceof DoubleValue)) {
                return;
            }

            throw badRequest("부호는 숫자 리터럴에만 사용할 수 있습니다.");
        }

        throw badRequest("조건의 값으로 지원하지 않는 표현식입니다: " + expression);
    }

    private void requireSingleExpression(ParenthesedExpressionList<?> parentheses) {
        if (parentheses.size() != 1) {
            throw badRequest("조건의 괄호에는 단일 표현식만 사용할 수 있습니다.");
        }
    }

    private void checkDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw badRequest("조건식의 중첩 깊이가 허용 범위를 초과했습니다.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
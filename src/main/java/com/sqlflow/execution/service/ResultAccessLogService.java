package com.sqlflow.execution.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ResultAccessLog;
import com.sqlflow.execution.mapper.ResultAccessLogMapper;

/**
 * 결과 접근 로그를 독립된 Oracle 트랜잭션으로 저장한다.
 *
 * 저장·커밋 실패 시 예외를 전달하여 정상 결과 제공을 차단한다.
 */
@Service
public class ResultAccessLogService {

    private static final Logger log =
            LoggerFactory.getLogger(ResultAccessLogService.class);

    private static final Set<String> ACTION_TYPES =
            Set.of("PREVIEW", "PREVIEW_MORE", "DOWNLOAD");

    private static final Set<String> ACCESS_RESULTS =
    		Set.of("PREPARED", "DENIED", "ERROR");

    private final ResultAccessLogMapper resultAccessLogMapper;
    private final TransactionTemplate logTransaction;

    public ResultAccessLogService(
            ResultAccessLogMapper resultAccessLogMapper,
            @Qualifier("managementTransactionManager")
            PlatformTransactionManager transactionManager) {

        this.resultAccessLogMapper = resultAccessLogMapper;

        this.logTransaction = new TransactionTemplate(
                transactionManager);

        this.logTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.logTransaction.setReadOnly(false);
    }

    /**
     * 서버가 확인한 식별정보로만 호출한다.
     * resultId는 결과 정보가 없으면 null을 허용한다.
     */
    public void record(
            Long requestId,
            Long resultId,
            Long userId,
            String actionType,
            String accessResult) {

        validateInput(
                requestId,
                resultId,
                userId,
                actionType,
                accessResult);

        ResultAccessLog entry = new ResultAccessLog();

        entry.setRequestId(requestId);
        entry.setResultId(resultId);
        entry.setUserId(userId);
        entry.setActionType(actionType);
        entry.setAccessResult(accessResult);

        try {
            // 이 호출이 정상 종료되어야 커밋까지 완료된 것이다.
            logTransaction.executeWithoutResult(status -> {
                int inserted =
                        resultAccessLogMapper.insertAccessLog(entry);

                if (inserted != 1) {
                    throw new IllegalStateException(
                            "접근 로그가 정확히 한 건 저장되지 않았습니다.");
                }
            });

        } catch (RuntimeException exception) {
            // 로그 DB에 다시 기록하려고 재귀 호출하지 않는다.
            log.error(
                    "결과 접근 로그 저장 실패: "
                    + "requestId={}, resultId={}, userId={}, "
                    + "actionType={}, accessResult={}",
                    requestId,
                    resultId,
                    userId,
                    actionType,
                    accessResult,
                    exception);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "접근 기록을 저장하지 못해 결과를 제공할 수 없습니다. "
                    + "잠시 후 다시 시도해 주세요.",
                    exception);
        }
    }

    private void validateInput(
            Long requestId,
            Long resultId,
            Long userId,
            String actionType,
            String accessResult) {

        if (requestId == null || requestId <= 0) {
            throw new IllegalArgumentException(
                    "로그에 기록할 요청 ID가 필요합니다.");
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "로그에 기록할 사용자 ID가 필요합니다.");
        }

        if (resultId != null && resultId <= 0) {
            throw new IllegalArgumentException(
                    "결과 ID는 양수 또는 null이어야 합니다.");
        }

        if (actionType == null || !ACTION_TYPES.contains(actionType)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 접근 동작입니다.");
        }

        if (accessResult == null
                || !ACCESS_RESULTS.contains(accessResult)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 접근 결과입니다.");
        }
    }
}
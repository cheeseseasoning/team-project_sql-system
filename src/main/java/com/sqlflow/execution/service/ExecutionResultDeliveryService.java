package com.sqlflow.execution.service;

import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionResultFile;
import com.sqlflow.execution.domain.ResultAccessLogContext;
import com.sqlflow.execution.dto.ExecutionResultPreviewResponse;
import com.sqlflow.execution.mapper.ResultAccessLogMapper;

import lombok.RequiredArgsConstructor;

/**
 * 결과 접근 검사·CSV 준비·접근 로그 저장을 연결한다.
 *
 * 정상 결과는 PREPARED 로그의 커밋이 끝난 뒤 반환한다.
 * PREPARED는 응답 준비 완료이며 전송·사용자 저장 완료를 의미하지 않는다.
 * 로그 저장 실패를 다시 ERROR로 기록하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionResultDeliveryService {

    private static final Logger log =
            LoggerFactory.getLogger(ExecutionResultDeliveryService.class);

    private final ExecutionResultService executionResultService;
    private final CsvPreviewService csvPreviewService;
    private final ResultAccessLogMapper resultAccessLogMapper;
    private final ResultAccessLogService resultAccessLogService;

    public ExecutionResultPreviewResponse preview(
            Long executionId,
            Long actorId,
            long offset,
            int limit) {

        String actionType =
                offset > 0 ? "PREVIEW_MORE" : "PREVIEW";

        return provide(
                executionId,
                actorId,
                actionType,
                () -> executionResultService.getMyResultFile(
                        executionId,
                        actorId),
                file -> csvPreviewService.read(
                        file.getFilePath(),
                        offset,
                        limit));
    }

    public Resource download(
            Long executionId,
            Long actorId) {

        return provide(
                executionId,
                actorId,
                "DOWNLOAD",
                () -> executionResultService.getMyDownloadFile(
                        executionId,
                        actorId),
                file -> csvPreviewService.getDownloadResource(
                        file.getFilePath()));
    }

    private <T> T provide(
            Long executionId,
            Long actorId,
            String actionType,
            Supplier<ExecutionResultFile> accessCheck,
            Function<ExecutionResultFile, T> prepareResponse) {

        ResultAccessLogContext context =
                loadContext(executionId, actorId, actionType);

        ExecutionResultFile file;

        // 1. 기존 이력 자격·파일 상태·데이터 및 반출 정책 검사.
        try {
            file = accessCheck.get();

        } catch (RuntimeException exception) {
            String accessResult =
                    isAccessDenial(exception) ? "DENIED" : "ERROR";

            recordFailure(
                    context,
                    executionId,
                    actorId,
                    actionType,
                    accessResult,
                    exception);

            throw exception;
        }

        // 정상 접근인데 로그 식별정보가 없다면 제공하지 않는다.
        if (context == null) {
            log.error(
                    "결과 제공 중단: 로그 식별정보 없음. "
                    + "executionId={}, userId={}, actionType={}",
                    executionId,
                    actorId,
                    actionType);

            throw loggingUnavailable();
        }

        // 사전 조회 이후 최신 결과가 달라졌다면,
        // 실제 제공 대상으로 선택된 결과 ID를 기록한다.
        context.setResultId(file.getResultId());

        T response;

        // 2. 실제 미리보기 읽기 또는 다운로드 Resource 준비.
        try {
            response = prepareResponse.apply(file);

        } catch (RuntimeException exception) {
            // 잘못된 미리보기 offset·limit은 요청 차단으로 기록한다.
            boolean badRequest =
                    exception instanceof ResponseStatusException status
                    && status.getStatusCode().value() == 400;

            recordFailure(
                    context,
                    executionId,
                    actorId,
                    actionType,
                    badRequest ? "DENIED" : "ERROR",
                    exception);

            throw exception;
        }

        // 3. 정상 로그를 커밋한 뒤에만 결과 반환.
        // 위 try/catch 밖이므로 로그 실패를 다시 기록하지 않는다.
        resultAccessLogService.record(
                context.getRequestId(),
                context.getResultId(),
                context.getUserId(),
                actionType,
        		"PREPARED");
        
        return response;
    }

    private ResultAccessLogContext loadContext(
            Long executionId,
            Long actorId,
            String actionType) {

        if (actorId == null || actorId <= 0) {
            log.warn(
                    "DB 로그 기록 불가: 로그인 사용자 없음. "
                    + "executionId={}, actionType={}",
                    executionId,
                    actionType);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (executionId == null || executionId <= 0) {
            log.warn(
                    "DB 로그 기록 불가: 실행 ID 오류. "
                    + "executionId={}, userId={}, actionType={}",
                    executionId,
                    actorId,
                    actionType);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 실행 ID를 입력하세요.");
        }

        try {
            return resultAccessLogMapper.selectAccessContext(
                    executionId,
                    actorId);

        } catch (RuntimeException exception) {
            log.error(
                    "결과 접근 로그 식별정보 조회 실패: "
                    + "executionId={}, userId={}, actionType={}",
                    executionId,
                    actorId,
                    actionType,
                    exception);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "접근 기록에 필요한 정보를 확인하지 못해 "
                    + "결과를 제공할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                    exception);
        }
    }

    private boolean isAccessDenial(RuntimeException exception) {
        if (!(exception instanceof ResponseStatusException status)) {
            return false;
        }

        int code = status.getStatusCode().value();

        return code >= 400 && code < 500;
    }

    private void recordFailure(
            ResultAccessLogContext context,
            Long executionId,
            Long actorId,
            String actionType,
            String accessResult,
            RuntimeException originalException) {

        if (context == null) {
            // 존재하지 않는 실행·사용자는 필수 FK를 충족할 수 없다.
            // 클라이언트에는 내부 식별정보를 노출하지 않는다.
            log.warn(
                    "DB 접근 로그 기록 불가: 식별정보 없음. "
                    + "executionId={}, userId={}, "
                    + "actionType={}, accessResult={}",
                    executionId,
                    actorId,
                    actionType,
                    accessResult);

            return;
        }

        try {
            resultAccessLogService.record(
                    context.getRequestId(),
                    context.getResultId(),
                    context.getUserId(),
                    actionType,
                    accessResult);

        } catch (ResponseStatusException loggingException) {
            // 원래 거부·파일 오류도 진단할 수 있도록 보존한다.
            // 로그 실패 시 최종 응답은 503으로 대체된다.
            loggingException.addSuppressed(originalException);
            throw loggingException;
        }
    }

    private ResponseStatusException loggingUnavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "접근 기록을 저장하지 못해 결과를 제공할 수 없습니다. "
                + "잠시 후 다시 시도해 주세요.");
    }
}
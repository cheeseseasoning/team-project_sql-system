package com.sqlflow;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SecurityConfig;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.controller.ExecutionResultController;
import com.sqlflow.execution.domain.ExecutionResultFile;
import com.sqlflow.execution.domain.ResultAccessLogContext;
import com.sqlflow.execution.dto.ExecutionResultPreviewResponse;
import com.sqlflow.execution.mapper.ResultAccessLogMapper;
import com.sqlflow.execution.service.CsvPreviewService;
import com.sqlflow.execution.service.ExecutionResultDeliveryService;
import com.sqlflow.execution.service.ExecutionResultService;
import com.sqlflow.execution.service.ResultAccessLogService;

/**
 * 실제 MVC·Security·제공 서비스 연결을 검증한다.
 *
 * 권한 판단 서비스·CSV 처리·로그 DB 저장은 Mock으로 대체한다.
 * 실제 Oracle이나 공유폴더에는 접근하지 않는다.
 */
@WebMvcTest(controllers = ExecutionResultController.class)
@Import({
        SecurityConfig.class,
        ExecutionResultDeliveryService.class
})
class ExecutionResultAccessLogApiTest {
	
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityBootstrap {
    }

    private static final Long EXECUTION_ID = 10L;
    private static final Long REQUEST_ID = 20L;
    private static final Long RESULT_ID = 30L;
    private static final Long USER_ID = 40L;

    private static final String FILE_PATH = "test-result.csv";

    private static final String PREVIEW_URL =
            "/api/execution-results/mine/{executionId}/preview";

    private static final String DOWNLOAD_URL =
            "/api/execution-results/mine/{executionId}/csv";

    private static final String LOG_FAILURE_MESSAGE =
            "접근 기록을 저장하지 못해 결과를 제공할 수 없습니다. "
            + "잠시 후 다시 시도해 주세요.";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ExecutionResultService executionResultService;

    @MockitoBean
    private CsvPreviewService csvPreviewService;

    @MockitoBean
    private ResultAccessLogMapper resultAccessLogMapper;

    @MockitoBean
    private ResultAccessLogService resultAccessLogService;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }
    
    @Test
    void firstPreviewReturnsDataAndRecordsOnePreparedLog()
            throws Exception {

        preparePreview(0L);

        mockMvc.perform(
                    get(PREVIEW_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.columns[0]").value("이름"))
                .andExpect(jsonPath("$.rows[0][0]").value("테스트"))
                .andExpect(jsonPath("$.nextOffset").value(1));

        verify(executionResultService)
                .getMyResultFile(EXECUTION_ID, USER_ID);

        verifyLog("PREVIEW", "PREPARED");
    }

    @Test
    void additionalPreviewRecordsPreviewMore()
            throws Exception {

        preparePreview(25L);

        mockMvc.perform(
                    get(PREVIEW_URL, EXECUTION_ID)
                        .with(loginUser())
                        .param("offset", "25")
                        .param("limit", "25")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextOffset").value(26));

        verify(csvPreviewService).read(FILE_PATH, 25L, 25);

        verifyLog("PREVIEW_MORE", "PREPARED");
    }

    @Test
    void allowedDownloadReturnsOriginalBytesAndRecordsPrepared()
            throws Exception {

        byte[] csv = prepareDownload();

        mockMvc.perform(
                    get(DOWNLOAD_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("text/csv")))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sqlflow-execution-10.csv\""))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"))
                .andExpect(content().bytes(csv));

        verify(executionResultService)
                .getMyDownloadFile(EXECUTION_ID, USER_ID);

        verifyLog("DOWNLOAD", "PREPARED");
    }

    @Test
    void blockedExportReturns403AndRecordsDeniedWithoutReadingFile()
            throws Exception {

        prepareContext();

        String message = "CSV 다운로드가 제한된 결과입니다.";

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        message));

        mockMvc.perform(
                    get(DOWNLOAD_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(header()
                        .doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        verifyLog("DOWNLOAD", "DENIED");
        verifyNoInteractions(csvPreviewService);
    }

    @Test
    void deniedPreviewReturns403AndRecordsDenied()
            throws Exception {

        prepareContext();

        String message = "민감 데이터 조회 권한이 없습니다.";

        when(executionResultService.getMyResultFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        message));

        mockMvc.perform(
                    get(PREVIEW_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.rows").doesNotExist());

        verifyLog("PREVIEW", "DENIED");
        verifyNoInteractions(csvPreviewService);
    }

    @Test
    void missingPhysicalFileReturns404AndRecordsError()
            throws Exception {

        prepareContext();

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());

        String message = "저장된 CSV 파일을 찾을 수 없습니다.";

        when(csvPreviewService.getDownloadResource(FILE_PATH))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        message));

        mockMvc.perform(
                    get(DOWNLOAD_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(header()
                        .doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        verifyLog("DOWNLOAD", "ERROR");
    }

    @Test
    void previewLogFailureReturns503WithoutPreviewData()
            throws Exception {

        preparePreview(0L);

        doThrow(logFailure())
                .when(resultAccessLogService)
                .record(
                        REQUEST_ID,
                        RESULT_ID,
                        USER_ID,
                        "PREVIEW",
                        "PREPARED");

        mockMvc.perform(
                    get(PREVIEW_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                        .value(LOG_FAILURE_MESSAGE))
                .andExpect(jsonPath("$.rows").doesNotExist());

        // PREPARED 로그 실패를 ERROR 로그로 재시도하지 않는다.
        verifyLog("PREVIEW", "PREPARED");
    }

    @Test
    void downloadLogFailureReturns503WithoutCsvResponse()
            throws Exception {

        prepareDownload();

        doThrow(logFailure())
                .when(resultAccessLogService)
                .record(
                        REQUEST_ID,
                        RESULT_ID,
                        USER_ID,
                        "DOWNLOAD",
                        "PREPARED");

        mockMvc.perform(
                    get(DOWNLOAD_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                        .value(LOG_FAILURE_MESSAGE))
                .andExpect(header()
                        .doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        verifyLog("DOWNLOAD", "PREPARED");
    }

    @Test
    void deniedLogFailureReturns503RatherThan403()
            throws Exception {

        prepareContext();

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "CSV 반출이 차단되었습니다."));

        doThrow(logFailure())
                .when(resultAccessLogService)
                .record(
                        REQUEST_ID,
                        RESULT_ID,
                        USER_ID,
                        "DOWNLOAD",
                        "DENIED");

        mockMvc.perform(
                    get(DOWNLOAD_URL, EXECUTION_ID)
                        .with(loginUser())
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value(LOG_FAILURE_MESSAGE));

        verifyLog("DOWNLOAD", "DENIED");
        verifyNoInteractions(csvPreviewService);
    }

    @Test
    void anonymousRequestsAreRejectedBeforeDeliveryService()
            throws Exception {

        mockMvc.perform(get(PREVIEW_URL, EXECUTION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."));

        mockMvc.perform(get(DOWNLOAD_URL, EXECUTION_ID))
                .andExpect(status().isUnauthorized());

        // Security가 먼저 차단하므로 로그 DB 경로에도 진입하지 않는다.
        verifyNoInteractions(
                executionResultService,
                csvPreviewService,
                resultAccessLogMapper,
                resultAccessLogService);
    }

    private void prepareContext() {
        ResultAccessLogContext context = new ResultAccessLogContext();

        context.setRequestId(REQUEST_ID);
        context.setResultId(RESULT_ID);
        context.setUserId(USER_ID);

        when(resultAccessLogMapper.selectAccessContext(
                EXECUTION_ID, USER_ID))
                .thenReturn(context);
    }

    private void preparePreview(long offset) {
        prepareContext();

        when(executionResultService.getMyResultFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());

        when(csvPreviewService.read(FILE_PATH, offset, 25))
                .thenReturn(new ExecutionResultPreviewResponse(
                        List.of("이름"),
                        List.of(List.of("테스트")),
                        offset + 1,
                        false));
    }

    private byte[] prepareDownload() {
        prepareContext();

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());

        byte[] csv =
                "\uFEFF이름\r\n테스트\r\n".getBytes(StandardCharsets.UTF_8);

        when(csvPreviewService.getDownloadResource(FILE_PATH))
                .thenReturn(new ByteArrayResource(csv));

        return csv;
    }

    private ExecutionResultFile resultFile() {
        ExecutionResultFile file = new ExecutionResultFile();

        file.setExecutionId(EXECUTION_ID);
        file.setQueryId(50L);
        file.setResultId(RESULT_ID);
        file.setStatus("SUCCESS");
        file.setFilePath(FILE_PATH);

        return file;
    }

    private ResponseStatusException logFailure() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                LOG_FAILURE_MESSAGE);
    }

    private void verifyLog(
            String actionType,
            String accessResult) {

        verify(resultAccessLogService)
                .record(
                        REQUEST_ID,
                        RESULT_ID,
                        USER_ID,
                        actionType,
                        accessResult);

        verifyNoMoreInteractions(resultAccessLogService);
    }

    private RequestPostProcessor loginUser() {
        UserAccount account = new UserAccount();

        account.setUserId(USER_ID);
        account.setDeptId(1L);
        account.setLoginId("result-log-api-test");
        account.setPasswordHash("unused");
        account.setUserName("결과 로그 API 테스트");
        account.setEnabled("Y");

        return user(new SqlFlowUserDetails(account, List.of()));
    }
}
package com.sqlflow;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.view.controller.ViewQueryController;
import com.sqlflow.view.service.ViewQueryService;

/**
 * 서비스 오류의 상태 코드와 안내 메시지가 HTTP 응답에 포함되는지 검증한다.
 * 실제 DB와 로그인·권한 검사는 이 테스트 범위에 포함하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ViewQueryControllerTest {

    @Mock
    private ViewQueryService viewQueryService;

    @InjectMocks
    private ViewQueryController viewQueryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(viewQueryController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void forbiddenResponseIncludesMessage() throws Exception {

        assertErrorResponse(
                HttpStatus.FORBIDDEN,
                "현재 소속 부서를 확인할 수 없습니다.");
    }

    @Test
    void notFoundResponseIncludesMessage() throws Exception {

        assertErrorResponse(
                HttpStatus.NOT_FOUND,
                "현재 업무 DB에서 해당 뷰를 찾을 수 없습니다.");
    }

    @Test
    void conflictResponseIncludesMessage() throws Exception {

        assertErrorResponse(
                HttpStatus.CONFLICT,
                "같은 객체의 리소스 등록이 중복되어 있습니다.");
    }

    @Test
    void missingReasonUsesFallbackMessage() throws Exception {

        assertErrorResponse(
                HttpStatus.CONFLICT,
                null,
                "뷰 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private void assertErrorResponse(HttpStatus httpStatus, String reason) throws Exception {

        assertErrorResponse(httpStatus, reason, reason);
    }

    private void assertErrorResponse(
            HttpStatus httpStatus,
            String reason,
            String expectedMessage) throws Exception {

        // 인증 처리를 제외하므로 사용자 ID는 null로 서비스에 전달된다.
        when(viewQueryService.getViewDetail("sqlflow_demo", "v_active_product_catalog", null))
                .thenThrow(new ResponseStatusException(httpStatus, reason));

        mockMvc.perform(get("/api/views/detail")
                        .param("schemaName", "sqlflow_demo")
                        .param("objectName", "v_active_product_catalog"))
                .andExpect(status().is(httpStatus.value()))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }
}
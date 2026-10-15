package com.sqlflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SqlFlowApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Test void generalSubmissionReturnsResultsAndCsv() throws Exception {
        var body=json.writeValueAsString(new DemoService.Draft("API 일반 조회","검증용 요청",List.of(DemoService.GENERAL_SQL)));
        String response=mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("COMPLETED")).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();
        mvc.perform(get("/api/requests/{id}/results/1",id)).andExpect(status().isOk()).andExpect(jsonPath("$.rows",hasSize(4))).andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/requests/{id}/results/1/csv",id)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",containsString("attachment")));
    }
    @Test void sensitiveViewMakesEntireMixedRequestPending() throws Exception {
        String response=mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new DemoService.Draft("혼합 조회","민감 정책 확인",List.of(DemoService.GENERAL_SQL,DemoService.SENSITIVE_SQL))))).andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("SENSITIVE")).andExpect(jsonPath("$.items[*].state",everyItem(is("PENDING")))).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();mvc.perform(get("/api/requests/{id}/results/1",id)).andExpect(status().isForbidden());
    }
    @Test void unsupportedSqlAndChangesAreRejected() throws Exception {
        for(String sql:List.of("DELETE FROM V_SALES_SUMMARY","SELECT * FROM employees",DemoService.GENERAL_SQL+" DROP TABLE users;"))
            mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new DemoService.Draft("허용 외 SQL","거부 검증",List.of(sql))))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/requests/Q-20261014-012").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isMethodNotAllowed());
    }
    @Test void invalidCountAndMissingPurposeCannotBeSubmitted() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new DemoService.Draft("제목","",List.of(DemoService.GENERAL_SQL))))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new DemoService.Draft("제목","목적",java.util.Collections.nCopies(6,DemoService.GENERAL_SQL))))).andExpect(status().isBadRequest());
    }
    @Test void failedSqlAndUnknownIdsHaveNoDownload() throws Exception {
        mvc.perform(get("/api/requests/Q-20261013-008/results/2/csv")).andExpect(status().isConflict());
        mvc.perform(get("/api/requests/unknown")).andExpect(status().isNotFound());
    }
    @Test void aiRejectsUnauthorizedPrompt() throws Exception {
        mvc.perform(post("/api/sql/generate").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("prompt","직원 급여 조회")))).andExpect(status().isForbidden());
    }
}

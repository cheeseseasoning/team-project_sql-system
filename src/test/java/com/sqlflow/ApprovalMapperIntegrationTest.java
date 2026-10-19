package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.approval.mapper.ApprovalMapper;

@SpringBootTest
class ApprovalMapperIntegrationTest {

    @Autowired
    private ApprovalMapper approvalMapper;

    @Test
    void candidateQueriesExecuteAgainstOracleSchema() {
        assertNotNull(approvalMapper.selectDepartmentApprovers(-1L));
        assertNotNull(approvalMapper.selectDbApprovers(-1L));
    }
}

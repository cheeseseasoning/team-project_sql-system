package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.execution.mapper.ExecutionBatchMapper;

@SpringBootTest
class ExecutionBatchAuthorizationMapperIntegrationTest {

    @Autowired
    private ExecutionBatchMapper executionBatchMapper;

    @Test
    void nonexistentUserIsNotAnActiveDbTeamMember() {
        assertFalse(executionBatchMapper.isActiveDbTeamMember(Long.MIN_VALUE));
    }
}

package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.execution.domain.QueryObjectName;

/**
 * 실제 관리 DB에서 접근 정책 조회 SQL과 결과 매핑을 확인한다.
 *
 * 테스트용 부서·리소스·정책을 생성하며 각 테스트 종료 시 롤백한다.
 * 업무 DB 접속이나 실제 업무 테이블 생성은 하지 않는다.
 */
@SpringBootTest
@Transactional(transactionManager = "managementTransactionManager")
class DataAccessPolicyMapperIntegrationTest {

    @Autowired
    private DataAccessPolicyMapper policyMapper;

    private JdbcTemplate jdbcTemplate;

    @Autowired
    void configureManagementJdbc(@Qualifier("managementDataSource") javax.sql.DataSource dataSource) {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private Long deptId;
    private Long otherDeptId;

    private Long salesResourceId;
    private Long customersResourceId;

    private QueryObjectName sales;
    private QueryObjectName customers;

    @BeforeEach
    void setUp() {
        deptId = nextTestId();
        otherDeptId = nextTestId();

        insertDepartment(deptId);
        insertDepartment(otherDeptId);

        // 실제 업무 객체와 겹치지 않는 테스트 전용 이름을 사용한다.
        String owner = "TEST_" + UUID.randomUUID()
                .toString().replace("-", "").toUpperCase();

        sales = new QueryObjectName(owner, "SALES");
        customers = new QueryObjectName(owner, "CUSTOMERS");

        salesResourceId = insertResource(sales, "Y");
        customersResourceId = insertResource(customers, "Y");

        insertPolicy(salesResourceId, deptId, "GENERAL", "Y");
        insertPolicy(customersResourceId, deptId, "SENSITIVE", "Y");
    }

    @Test
    void readsMultiplePoliciesAndMapsObjectNames() {
        var policies = policyMapper.selectPolicies(
                deptId, Set.of(sales, customers));

        assertEquals(2, policies.size());

        var byResourceId = policies.stream()
                .collect(Collectors.toMap(
                        policy -> policy.getResourceId(),
                        policy -> policy));

        var general = byResourceId.get(salesResourceId);
        assertEquals(sales.owner(), general.getOwner());
        assertEquals(sales.objectName(), general.getObjectName());
        assertEquals("GENERAL", general.getAccessLevel());

        var sensitive = byResourceId.get(customersResourceId);
        assertEquals(customers.owner(), sensitive.getOwner());
        assertEquals(customers.objectName(), sensitive.getObjectName());
        assertEquals("SENSITIVE", sensitive.getAccessLevel());
    }

    @Test
    void excludesPoliciesBelongingToAnotherDepartment() {
        var policies = policyMapper.selectPolicies(
                otherDeptId, Set.of(sales, customers));

        assertTrue(policies.isEmpty());
    }

    @Test
    void excludesDisabledResource() {
        jdbcTemplate.update("""
                UPDATE DATA_RESOURCE
                SET ENABLED = 'N'
                WHERE RESOURCE_ID = ?
                """, salesResourceId);

        var policies = policyMapper.selectPolicies(
                deptId, Set.of(sales));

        assertTrue(policies.isEmpty());
    }

    @Test
    void excludesDisabledPolicy() {
        jdbcTemplate.update("""
                UPDATE DATA_ACCESS_POLICY
                SET ENABLED = 'N'
                WHERE RESOURCE_ID = ?
                  AND DEPT_ID = ?
                """, customersResourceId, deptId);

        var policies = policyMapper.selectPolicies(
                deptId, Set.of(customers));

        assertTrue(policies.isEmpty());
    }

    @Test
    void sameObjectNameInAnotherSchemaDoesNotMatch() {
        QueryObjectName anotherSchema = new QueryObjectName(
                sales.owner() + "_OTHER", sales.objectName());

        var policies = policyMapper.selectPolicies(
                deptId, Set.of(anotherSchema));

        assertTrue(policies.isEmpty());
    }

    @Test
    void unregisteredObjectDoesNotMatch() {
        QueryObjectName missing = new QueryObjectName(
                sales.owner(), "NOT_REGISTERED");

        var policies = policyMapper.selectPolicies(
                deptId, Set.of(missing));

        assertTrue(policies.isEmpty());
    }

    @Test
    void emptyTargetsReturnNoPolicies() {
        var policies = policyMapper.selectPolicies(
                deptId, Set.of());

        assertTrue(policies.isEmpty());
    }

    private void insertDepartment(Long id) {
        jdbcTemplate.update("""
                INSERT INTO DEPARTMENT (
                    DEPT_ID,
                    DEPT_NAME
                ) VALUES (?, ?)
                """, id, "정책 테스트 부서 " + id);
    }

    private Long insertResource(
            QueryObjectName target,
            String enabled) {

        Long resourceId = nextTestId();

        jdbcTemplate.update("""
                INSERT INTO DATA_RESOURCE (
                    RESOURCE_ID,
                    SCHEMA_NAME,
                    OBJECT_NAME,
                    RESOURCE_NAME,
                    ENABLED,
                    CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                resourceId,
                target.owner(),
                target.objectName(),
                "정책 테스트 " + target.objectName(),
                enabled);

        return resourceId;
    }

    private void insertPolicy(
            Long resourceId,
            Long policyDeptId,
            String accessLevel,
            String enabled) {

        jdbcTemplate.update("""
                INSERT INTO DATA_ACCESS_POLICY (
                    POLICY_ID,
                    RESOURCE_ID,
                    DEPT_ID,
                    ACCESS_LEVEL,
                    EXPORT_POLICY,
                    ENABLED,
                    CREATED_AT
                ) VALUES (?, ?, ?, ?, 'BLOCK', ?, SYSTIMESTAMP)
                """,
                nextTestId(),
                resourceId,
                policyDeptId,
                accessLevel,
                enabled);
    }

    /**
     * 테스트에서만 사용하는 임의의 음수 ID.
     * 일반적인 양수 ID 및 운영 시퀀스 사용과 겹치지 않게 한다.
     */
    private Long nextTestId() {
        return -ThreadLocalRandom.current()
                .nextLong(1, Long.MAX_VALUE);
    }
}

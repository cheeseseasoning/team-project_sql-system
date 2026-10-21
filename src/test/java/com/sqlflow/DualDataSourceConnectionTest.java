package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.mysql.mapper.MySqlConnectionMapper;

@SpringBootTest
class DualDataSourceConnectionTest {

    @Autowired
    private MySqlConnectionMapper mySqlConnectionMapper;

    @Autowired
    @Qualifier("managementSqlSessionFactory")
    private SqlSessionFactory managementSqlSessionFactory;

    @Autowired
    @Qualifier("querySqlSessionFactory")
    private SqlSessionFactory querySqlSessionFactory;

    @Test
    void managementConnectionUsesOracle() throws Exception {
        try (var session = managementSqlSessionFactory.openSession()) {
            var metadata = session.getConnection().getMetaData();

            assertEquals("Oracle", metadata.getDatabaseProductName());
        }
    }

    @Test
    void queryConnectionUsesMySql() throws Exception {
        try (var session = querySqlSessionFactory.openSession()) {
            var metadata = session.getConnection().getMetaData();

            assertEquals("MySQL", metadata.getDatabaseProductName());
        }
    }

    @Test
    void mySqlMapperReadsUsingReaderAccount() {
        assertEquals(
                "sqlflow_demo",
                mySqlConnectionMapper.selectDatabase()
        );

        assertEquals(
                "sqlflow_reader@%",
                mySqlConnectionMapper.selectCurrentUser()
        );

        assertEquals(
                "MySQL connection success",
                mySqlConnectionMapper.selectMessage(1)
        );
    }
}
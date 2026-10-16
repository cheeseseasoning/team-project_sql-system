package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@MapperScan(basePackageClasses = DbConnectionTestMapper.class)
class DbConnectionTest {

    @Autowired
    private DbConnectionTestMapper mapper;

    @Test
    void oracleConnectionThroughMyBatis() {
        assertEquals(1, mapper.selectOne());
        assertEquals("SQLFLOW", mapper.selectUser());
    }
}
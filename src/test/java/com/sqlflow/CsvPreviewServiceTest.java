package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.execution.service.CsvPreviewService;

@SpringBootTest
class CsvPreviewServiceTest {

    @Autowired
    private CsvPreviewService csvPreviewService;

    @Test
    void readsFirst25RowsAndSpecialCharacters() {
        var result = csvPreviewService.read("sample.csv", 0, 25);

        assertEquals(9, result.columns().size());
        assertEquals("순번", result.columns().get(0));
        assertEquals(25, result.rows().size());

        // 첫 데이터 행과 다음 조회 위치
        assertEquals("1", result.rows().get(0).get(0));
        assertEquals(25L, result.nextOffset());
        assertTrue(result.hasMore());

        // BOM·한글·쉼표·큰따옴표·줄바꿈·빈 값 확인
        assertEquals("영업팀", result.rows().get(0).get(1));
        assertEquals("서울, 부산 지점 통합",
                result.rows().get(1).get(8));
        assertEquals("담당자 \"확인 완료\"",
                result.rows().get(2).get(8));
        assertEquals("첫 번째 줄\r\n두 번째 줄",
                result.rows().get(3).get(8));
        assertEquals("쉼표, 큰따옴표 \"포함\"\r\n다음 줄",
                result.rows().get(4).get(8));
        assertEquals("", result.rows().get(5).get(8));
    }

    @Test
    void readsNext25RowsWithoutRepeatingPreviousRows() {
        var first = csvPreviewService.read("sample.csv", 0, 25);

        var second = csvPreviewService.read(
                "sample.csv",
                first.nextOffset(),
                25
        );

        assertEquals(25, second.rows().size());
        assertEquals("26", second.rows().get(0).get(0));
        assertEquals("50", second.rows().get(24).get(0));
        assertEquals(50L, second.nextOffset());
        assertTrue(second.hasMore());
    }

    @Test
    void stopsAtPreviewLimitEvenWhenFileHasMoreRows() {
        // 90행부터 25행을 요청해도 100행까지만 반환
        var result = csvPreviewService.read("sample.csv", 90, 25);

        assertEquals(10, result.rows().size());
        assertEquals("91", result.rows().get(0).get(0));
        assertEquals("100", result.rows().get(9).get(0));
        assertEquals(100L, result.nextOffset());
        assertFalse(result.hasMore());
    }
}
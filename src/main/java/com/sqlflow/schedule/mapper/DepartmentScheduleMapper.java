package com.sqlflow.schedule.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepartmentScheduleMapper {

    record Schedule(Long scheduleId, LocalDate scheduleDate, String title, String memo, Long createdBy) {
    }

    @Select("""
        SELECT SCHEDULE_ID, SCHEDULE_DATE, TITLE, MEMO, CREATED_BY
        FROM DEPARTMENT_SCHEDULE
        WHERE DEPT_ID = #{deptId}
          AND SCHEDULE_DATE >= #{from}
          AND SCHEDULE_DATE < #{to}
        ORDER BY SCHEDULE_DATE, SCHEDULE_ID
        """)
    List<Schedule> findByMonth(
            @Param("deptId") Long deptId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Insert("""
        INSERT INTO DEPARTMENT_SCHEDULE (DEPT_ID, CREATED_BY, SCHEDULE_DATE, TITLE, MEMO)
        VALUES (#{deptId}, #{userId}, #{scheduleDate}, #{title}, #{memo,jdbcType=VARCHAR})
        """)
    void insert(
            @Param("deptId") Long deptId,
            @Param("userId") Long userId,
            @Param("scheduleDate") LocalDate scheduleDate,
            @Param("title") String title,
            @Param("memo") String memo);

    /** 같은 부서의 등록자 본인 일정만 한 번의 SQL로 삭제한다. */
    @Delete("""
        DELETE FROM DEPARTMENT_SCHEDULE
        WHERE SCHEDULE_ID = #{scheduleId}
          AND DEPT_ID = #{deptId}
          AND CREATED_BY = #{userId}
        """)
    int deleteOwned(
            @Param("scheduleId") Long scheduleId,
            @Param("deptId") Long deptId,
            @Param("userId") Long userId);
}

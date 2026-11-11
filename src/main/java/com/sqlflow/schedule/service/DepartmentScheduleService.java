package com.sqlflow.schedule.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.schedule.mapper.DepartmentScheduleMapper;
import com.sqlflow.schedule.mapper.DepartmentScheduleMapper.Schedule;

@Service
public class DepartmentScheduleService {

    private final DepartmentScheduleMapper mapper;

    public DepartmentScheduleService(DepartmentScheduleMapper mapper) {
        this.mapper = mapper;
    }

    public List<Schedule> findByMonth(SqlFlowUserDetails user, YearMonth month) {
        return mapper.findByMonth(requireDepartment(user), month.atDay(1), month.plusMonths(1).atDay(1));
    }

    public void create(SqlFlowUserDetails user, LocalDate scheduleDate, String title, String memo) {
        Long deptId = requireDepartment(user);
        String normalizedMemo = memo == null || memo.isBlank() ? null : memo.strip();

        // 저장할 부서와 등록자는 로그인 정보에서 가져온다.
        mapper.insert(deptId, user.getUserId(), scheduleDate, title.strip(), normalizedMemo);
    }

    private Long requireDepartment(SqlFlowUserDetails user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long deptId = user.getDeptId();
        if (deptId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "소속 부서가 없는 계정입니다.");
        }
        return deptId;
    }

    public void delete(SqlFlowUserDetails user, Long scheduleId) {
        Long deptId = requireDepartment(user);
        if (mapper.deleteOwned(scheduleId, deptId, user.getUserId()) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "삭제할 수 있는 일정이 없거나 이미 삭제되었습니다.");
        }
    }
}

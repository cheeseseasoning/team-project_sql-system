package com.sqlflow.schedule.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.schedule.mapper.DepartmentScheduleMapper.Schedule;
import com.sqlflow.schedule.service.DepartmentScheduleService;

@RestController
@RequestMapping("/api/department-schedules")
public class DepartmentScheduleController {

    private final DepartmentScheduleService service;

    public DepartmentScheduleController(DepartmentScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Schedule> getSchedules(
            @AuthenticationPrincipal SqlFlowUserDetails user,
            @RequestParam("month") String month) {
        YearMonth requestedMonth;
        try {
            requestedMonth = YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 월은 YYYY-MM 형식으로 입력하세요.");
        }

        return service.findByMonth(user, requestedMonth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createSchedule(
            @AuthenticationPrincipal SqlFlowUserDetails user,
            @Valid @RequestBody CreateScheduleRequest request) {
        // 소속 부서와 등록자는 서비스에서 로그인 계정 기준으로 결정한다.
        service.create(user, request.scheduleDate(), request.title(), request.memo());
    }

    public record CreateScheduleRequest(
            @NotNull LocalDate scheduleDate,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String memo) {
    }
}
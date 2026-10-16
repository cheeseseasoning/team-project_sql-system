package com.sqlflow.request.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.mapper.RequestMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestMapper requestMapper;

    @Transactional
    public Long createDraft(RequestDraftCreateRequest input) {

        // 로그인 연동 전까지 사용하는 개발용 사용자·부서
        Long drafterId = 1L;
        Long deptId = 1L;

        // 1. 요청 ID 발급
        Long requestId = requestMapper.selectNextRequestId();

        // 2. 요청서 구성
        QueryRequest request = new QueryRequest();
        request.setRequestId(requestId);
        request.setRequestNo(createRequestNo(requestId));
        request.setDrafterId(drafterId);
        request.setDeptId(deptId);
        request.setTitle(input.getTitle().strip());
        request.setPurpose(input.getPurpose().strip());

        // 3. 요청서 저장
        requestMapper.insertRequest(request);

        // 4. SQL 항목 저장
        for (int index = 0; index < input.getSqls().size(); index++) {
            QueryItem item = new QueryItem();

            item.setRequestId(requestId);
            item.setQueryOrder(index + 1);
            item.setQueryTitle("SQL " + (index + 1));
            item.setSqlText(input.getSqls().get(index));
            item.setAuthoringType("MANUAL");

            requestMapper.insertItem(item);
        }

        return requestId;
    }

    private String createRequestNo(Long requestId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        return "Q-"
                + today.toString().replace("-", "")
                + "-"
                + requestId;
    }
}
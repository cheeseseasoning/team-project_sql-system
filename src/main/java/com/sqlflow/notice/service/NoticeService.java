package com.sqlflow.notice.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.notice.domain.Notice;
import com.sqlflow.notice.mapper.NoticeMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "managementTransactionManager", readOnly = true)
public class NoticeService {

    private final NoticeMapper noticeMapper;

    /** 전체 건수와 페이지 수 계산에 사용할 공지 수를 반환한다. */
    public long countNotices() {
        return noticeMapper.countNotices();
    }

    /**
     * 1부터 시작하는 페이지 번호를 DB 조회 시작 위치로 변환한다.
     * 한 번에 조회할 수 있는 개수는 최대 100개로 제한한다.
     */
    public List<Notice> getNoticePage(int page, int size) {

        if (page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지는 1 이상, 조회 개수는 1~100이어야 합니다.");
        }

        long offset = (long) (page - 1) * size;

        return noticeMapper.selectNoticePage(offset, size);
    }

    /** 공지 상세를 조회하고, 존재하지 않으면 404로 처리한다. */
    public Notice getNotice(Long noticeId) {

        if (noticeId == null || noticeId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 공지 번호를 입력하세요.");
        }

        Notice notice = noticeMapper.selectNoticeById(noticeId);

        if (notice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }

        return notice;
    }
}
package com.sqlflow.notice.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.notice.domain.Notice;
import com.sqlflow.notice.dto.NoticeCreateRequest;
import com.sqlflow.notice.mapper.NoticeMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "managementTransactionManager", readOnly = true)
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final UserAccountMapper userAccountMapper;

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
    
    /**
     * 현재 활성 시스템 관리자만 공지를 등록한다.
     * loginId는 요청 본문이 아닌 서버의 로그인 정보에서 전달받는다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public Long createNotice(NoticeCreateRequest request, String loginId) {

        UserAccount admin = requireSystemAdmin(loginId);

        validateNoticeInput(request);

        Long noticeId = noticeMapper.nextNoticeId();

        int inserted = noticeMapper.insertNotice(
                noticeId,
                request.title().strip(),
                request.content(),
                admin.getUserId());

        if (inserted != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "공지사항을 등록하지 못했습니다.");
        }

        return noticeId;
    }
    
    /** 기존 공지의 내용과 마지막 수정 정보를 저장한다. */
    @Transactional(transactionManager = "managementTransactionManager")
    public void updateNotice(Long noticeId, NoticeCreateRequest request, String loginId) {

        UserAccount admin = requireSystemAdmin(loginId);

        if (noticeId == null || noticeId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 공지 번호를 입력하세요.");
        }

        validateNoticeInput(request);

        int updated = noticeMapper.updateNotice(
                noticeId,
                request.title().strip(),
                request.content(),
                admin.getUserId());

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }
    }
    
    /** 시스템 관리자만 선택한 공지를 삭제할 수 있다. */
    @Transactional(transactionManager = "managementTransactionManager")
    public void deleteNotice(Long noticeId, String loginId) {

        requireSystemAdmin(loginId);

        if (noticeId == null || noticeId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 공지 번호를 입력하세요.");
        }

        if (noticeMapper.deleteNotice(noticeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }
    }

    /** 등록과 수정에 같은 제목·본문 검증 규칙을 적용한다. */
    private void validateNoticeInput(NoticeCreateRequest request) {

        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공지 제목을 입력하세요.");
        }

        if (request.title().length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공지 제목은 200자 이하여야 합니다.");
        }

        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공지 본문을 입력하세요.");
        }
    }

    /** 세션 생성 이후 계정 비활성화나 역할 회수가 있었는지 DB에서 다시 확인한다. */
    private UserAccount requireSystemAdmin(String loginId) {

        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        UserAccount account = userAccountMapper.selectByLoginId(loginId);

        if (account == null || !"Y".equals(account.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 계정만 공지사항을 관리할 수 있습니다.");
        }

        List<String> roles = userAccountMapper.selectRoleCodes(account.getUserId());

        if (!roles.contains("SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "시스템 관리자만 공지사항을 관리할 수 있습니다.");
        }

        return account;
    }
    
}
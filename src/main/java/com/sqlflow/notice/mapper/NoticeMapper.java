package com.sqlflow.notice.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.notice.domain.Notice;

@Mapper
public interface NoticeMapper {

    // 페이징 화면에 표시할 전체 공지 수.
    long countNotices();

    // 목록에서는 본문을 제외하고 필요한 정보만 조회한다.
    List<Notice> selectNoticePage(
            @Param("offset") long offset,
            @Param("size") int size);

    // 존재하지 않는 공지 번호는 null을 반환한다.
    Notice selectNoticeById(@Param("noticeId") Long noticeId);
}
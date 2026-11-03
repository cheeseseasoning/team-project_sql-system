<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <h1>공지사항</h1>
    <span class="caption">운영 및 이용 안내</span>
</div>

<section class="panel">
    <div class="table-scroll">
        <table>
            <thead>
                <tr>
                    <th>번호</th>
                    <th>제목</th>
                    <th>작성자</th>
                    <th>등록일</th>
                </tr>
            </thead>
            <tbody id="notices-body">
                <tr>
                    <td colspan="4">공지사항을 불러오는 중입니다.</td>
                </tr>
            </tbody>
        </table>
    </div>

    <div class="section-gap">
        <button id="previous-page" type="button" class="button" disabled>
            이전
        </button>
        <span id="page-info" class="caption" aria-live="polite"></span>
        <button id="next-page" type="button" class="button" disabled>
            다음
        </button>
    </div>
</section>

<article id="notice-detail" class="panel section-gap" hidden>
    <span id="notice-meta" class="caption"></span>
    <h2 id="notice-title" class="section-gap"></h2>
    <p id="notice-body" class="notice-body"></p>

    <button id="close-notice" type="button" class="button">
        목록만 보기
    </button>
</article>

<script>
    const noticePageSize = 10;

    let currentNoticePage = 1;
    let noticeTotalPages = 0;
    let noticeListLoading = false;
    let noticeDetailVersion = 0;

    // Oracle TIMESTAMP의 날짜 부분을 표시한다.
    function formatNoticeDate(value) {
        return value ? value.substring(0, 10) : '-';
    }

    function updateNoticePagination() {
        $('#page-info').textContent = noticeTotalPages === 0
            ? '등록된 공지사항이 없습니다.'
            : currentNoticePage + ' / ' + noticeTotalPages + ' 페이지';

        $('#previous-page').disabled = noticeListLoading || currentNoticePage <= 1;
        $('#next-page').disabled = noticeListLoading || currentNoticePage >= noticeTotalPages;
    }

    async function loadNotices(page) {
        if (noticeListLoading) {
            return;
        }

        noticeListLoading = true;
        updateNoticePagination();

        try {
            const data = await api('/notices?page=' + page + '&size=' + noticePageSize);

            currentNoticePage = data.page;
            noticeTotalPages = data.totalPages;

            if (data.notices.length === 0) {
                $('#notices-body').innerHTML =
                    '<tr><td colspan="4">표시할 공지사항이 없습니다.</td></tr>';
                return;
            }

            // 제목과 작성자 이름은 HTML로 해석되지 않도록 이스케이프한다.
            $('#notices-body').innerHTML = data.notices.map(notice =>
                '<tr>' +
                    '<td>' + esc(notice.noticeId) + '</td>' +
                    '<td><button type="button" class="table-link" data-notice="' +
                        esc(notice.noticeId) + '">' + esc(notice.title) + '</button></td>' +
                    '<td>' + esc(notice.createdByName) + '</td>' +
                    '<td>' + esc(formatNoticeDate(notice.createdAt)) + '</td>' +
                '</tr>'
            ).join('');
        } catch (error) {
            $('#notices-body').innerHTML =
                '<tr><td colspan="4">공지사항을 불러오지 못했습니다. 새로고침해 주세요.</td></tr>';

            notify(error.message, true);
        } finally {
            noticeListLoading = false;
            updateNoticePagination();
        }
    }

    async function openNotice(noticeId) {
        // 빠르게 다른 공지를 선택해도 이전 응답이 화면을 덮어쓰지 않게 한다.
        const version = ++noticeDetailVersion;
        $('#notice-detail').hidden = true;

        try {
            const notice = await api('/notices/' + encodeURIComponent(noticeId));

            if (version !== noticeDetailVersion) {
                return;
            }

            $('#notice-meta').textContent =
                notice.createdByName + ' · ' + formatNoticeDate(notice.createdAt);

            $('#notice-title').textContent = notice.title;

            // 본문에 HTML이나 스크립트가 있어도 일반 텍스트로 표시한다.
            $('#notice-body').textContent = notice.content;
            $('#notice-detail').hidden = false;

            history.replaceState(null, '', BASE + '/notices?id=' + encodeURIComponent(noticeId));
        } catch (error) {
            if (version === noticeDetailVersion) {
                notify(error.message, true);
            }
        }
    }

    // 목록 영역에 이벤트를 등록하면 페이지를 바꿔도 다시 등록할 필요가 없다.
    $('#notices-body').addEventListener('click', event => {
        const button = event.target.closest('[data-notice]');

        if (button) {
            openNotice(button.dataset.notice);
        }
    });

    $('#previous-page').addEventListener('click', () => {
        loadNotices(currentNoticePage - 1);
    });

    $('#next-page').addEventListener('click', () => {
        loadNotices(currentNoticePage + 1);
    });

    $('#close-notice').addEventListener('click', () => {
        noticeDetailVersion++;
        $('#notice-detail').hidden = true;
        history.replaceState(null, '', BASE + '/notices');
    });

    loadNotices(1);

    // 상세 링크로 직접 접속한 경우 목록 페이지와 별개로 본문을 조회한다.
    const initialNoticeId = new URLSearchParams(location.search).get('id');

    if (initialNoticeId) {
        openNotice(initialNoticeId);
    }
</script>

<%@ include file="fragments/bottom.jspf" %>
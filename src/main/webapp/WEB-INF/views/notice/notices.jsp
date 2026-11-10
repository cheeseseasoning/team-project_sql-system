<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="notices-page">
<div class="page-heading panel-aligned-heading">
    <h1>공지사항</h1>
</div>

<section id="notice-list-view" hidden>
    <div class="panel">
        <div class="table-scroll">
            <table class="notice-table">
                <thead>
                    <tr>
                        <th scope="col">제목</th>
                        <th scope="col">작성자</th>
                        <th scope="col">등록일</th>
                    </tr>
                </thead>
                <tbody id="notices-body">
                    <tr><td colspan="3">공지사항을 불러오는 중입니다.</td></tr>
                </tbody>
            </table>
        </div>
        <nav id="notice-pagination" class="notice-pagination" aria-label="공지사항 페이지" hidden></nav>
    </div>

    <div class="notice-list-actions">
        <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
            <button id="notice-create" type="button" class="button primary">
                공지 등록
            </button>
        </c:if>
    </div>
</section>

<section id="notice-detail-view" hidden>
    <article class="panel notice-detail-panel">
        <p id="notice-detail-status" role="status">공지사항을 불러오는 중입니다.</p>

        <div id="notice-detail-content" hidden>
            <h2 id="notice-title"></h2>
            <p id="notice-meta" class="caption"></p>
            <div id="notice-body" class="notice-body"></div>
        </div>
    </article>

    <div class="notice-detail-footer">
        <a id="notice-back" class="button">목록</a>

        <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
            <div id="notice-detail-actions" hidden>
                <button id="notice-edit" type="button" class="button">수정</button>
                <button id="notice-delete" type="button" class="button">삭제</button>
            </div>
        </c:if>
    </div>
</section>

<c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
    <section id="notice-editor" class="panel" hidden>
        <h2 id="notice-editor-heading"></h2>

        <form id="notice-form">
            <div class="section-gap">
                <label for="notice-form-title">제목</label>
                <input id="notice-form-title" name="title"
                       type="text" maxlength="200" required>
            </div>

            <div class="section-gap">
                <label for="notice-form-content">본문</label>
                <textarea id="notice-form-content" name="content"
                          rows="10" required></textarea>
            </div>

            <div class="section-gap">
                <button id="notice-save" type="submit" class="button primary">
                    저장
                </button>
                <button id="notice-cancel" type="button" class="button">
                    취소
                </button>
            </div>
        </form>
    </section>
</c:if>
</div>

<script>
    (() => {
        const params = new URLSearchParams(location.search);
        const requestedPage = Number(params.get('page') || 1);
        const page = Number.isInteger(requestedPage)
            && requestedPage >= 1 && requestedPage <= 2147483647
            ? requestedPage : 1;
        const noticeId = params.get('id');
        const pageSize = 10;

        const listView = $('#notice-list-view');
        const detailView = $('#notice-detail-view');
        const detailActions = $('#notice-detail-actions');
        const editor = $('#notice-editor');
        const form = $('#notice-form');

        let selectedNotice = null;

        function listUrl(pageNumber) {
            return BASE + '/notices?page=' + pageNumber;
        }

        function detailUrl(id, pageNumber) {
            return listUrl(pageNumber) + '&id=' + encodeURIComponent(id);
        }

        function formatDate(value) {
            return value ? value.substring(0, 10).replace(/-/g, '.') : '-';
        }

        function renderPagination(totalPages) {
            const pagination = $('#notice-pagination');
            const fragment = document.createDocumentFragment();
            const groupSize = 10;
            const first = Math.floor((page - 1) / groupSize) * groupSize + 1;
            const last = Math.min(first + groupSize - 1, totalPages);

            function appendLink(label, target, accessibleLabel, disabled = false) {
                const item = document.createElement(disabled ? 'span' : 'a');
                item.className = 'button';
                item.textContent = label;
                item.setAttribute('aria-label', accessibleLabel);
                if (disabled) {
                    item.setAttribute('aria-disabled', 'true');
                } else {
                    item.href = listUrl(target);
                    if (target === page) {
                        item.setAttribute('aria-current', 'page');
                    }
                }
                fragment.appendChild(item);
            }

            appendLink('‹ 이전', page - 1, '이전 페이지', page <= 1);
            for (let number = first; number <= last; number++) {
                appendLink(String(number), number, number + '페이지');
            }
            appendLink('다음 ›', page + 1, '다음 페이지', page >= totalPages);
            pagination.replaceChildren(fragment);
            pagination.hidden = totalPages === 0;
        }

        function showView(view) {
            listView.hidden = view !== listView;
            detailView.hidden = view !== detailView;

            if (editor) {
                editor.hidden = view !== editor;
            }
        }

        async function loadList() {
            showView(listView);

            try {
                const data = await api('/notices?page=' + page + '&size=' + pageSize);

                // 마지막 페이지의 마지막 공지를 삭제했으면 이전 페이지로 이동한다.
                const lastPage = Math.max(1, data.totalPages);

                if (page > lastPage) {
                    location.replace(listUrl(lastPage));
                    return;
                }

                $('#notices-body').innerHTML = data.notices.length
                    ? data.notices.map(notice =>
                        '<tr>' +
                            '<td><a class="table-link" href="' +
                                esc(detailUrl(notice.noticeId, page)) + '">' +
                                esc(notice.title) + '</a></td>' +
                            '<td>' + esc(notice.createdByName || '-') + '</td>' +
                            '<td>' + esc(formatDate(notice.createdAt)) + '</td>' +
                        '</tr>'
                    ).join('')
                    : '<tr><td colspan="3">등록된 공지사항이 없습니다.</td></tr>';

                renderPagination(data.totalPages);
            } catch (error) {
                $('#notices-body').innerHTML =
                    '<tr><td colspan="3">공지사항을 불러오지 못했습니다.</td></tr>';
                notify(error.message, true);
            }
        }

        async function loadDetail() {
            showView(detailView);
            $('#notice-back').href = listUrl(page);

            try {
                const notice = await api('/notices/' + encodeURIComponent(noticeId));
                selectedNotice = notice;

                $('#notice-title').textContent = notice.title;

                const metadata = [
                    notice.createdByName || '-',
                    formatDate(notice.updatedAt || notice.createdAt)
                ];

                $('#notice-meta').textContent = metadata.join(' · ');

                // 본문은 텍스트로 표시해 입력한 HTML이 실행되지 않도록 한다.
                $('#notice-body').textContent = notice.content;
                $('#notice-detail-status').hidden = true;
                $('#notice-detail-content').hidden = false;

                if (detailActions) {
                    detailActions.hidden = false;
                }
            } catch (error) {
                $('#notice-detail-status').textContent = error.message;
            }
        }

        if (form) {
            const titleInput = $('#notice-form-title');
            const contentInput = $('#notice-form-content');
            const saveButton = $('#notice-save');
            const cancelButton = $('#notice-cancel');
            const deleteButton = $('#notice-delete');

            // 등록과 수정은 같은 폼을 사용한다. 수정할 때만 기존 값을 채운다.
            function openEditor(notice) {
                $('#notice-editor-heading').textContent = notice ? '공지 수정' : '공지 등록';
                titleInput.value = notice ? notice.title : '';
                contentInput.value = notice ? notice.content : '';
                showView(editor);
                titleInput.focus();
            }

            $('#notice-create').addEventListener('click', () => openEditor(null));

            $('#notice-edit').addEventListener('click', () => {
                if (selectedNotice) {
                    openEditor(selectedNotice);
                }
            });

            cancelButton.addEventListener('click', () => {
                showView(noticeId ? detailView : listView);
                $(noticeId ? '#notice-edit' : '#notice-create').focus();
            });

            form.addEventListener('submit', async event => {
                event.preventDefault();

                if (saveButton.disabled) {
                    return;
                }

                const title = titleInput.value.trim();
                const content = contentInput.value;

                if (!title || !content.trim()) {
                    notify('공지 제목과 본문을 입력하세요.', true);
                    return;
                }

                const path = noticeId
                    ? '/admin/notices/' + encodeURIComponent(noticeId)
                    : '/admin/notices';

                saveButton.disabled = true;
                cancelButton.disabled = true;

                try {
                    const result = await api(path, {
                        method: noticeId ? 'PUT' : 'POST',
                        body: JSON.stringify({ title, content })
                    });

                    // 수정 후에는 보던 목록 페이지를 유지하고, 새 공지는 1페이지로 연결한다.
                    location.assign(detailUrl(noticeId || result.noticeId, noticeId ? page : 1));
                } catch (error) {
                    notify(error.message, true);
                } finally {
                    saveButton.disabled = false;
                    cancelButton.disabled = false;
                }
            });

            deleteButton.addEventListener('click', async () => {
                if (!selectedNotice || deleteButton.disabled) {
                    return;
                }

                if (!window.confirm('이 공지사항을 삭제하시겠습니까?')) {
                    return;
                }

                deleteButton.disabled = true;
                $('#notice-edit').disabled = true;

                try {
                    await api('/admin/notices/' + encodeURIComponent(noticeId), {
                        method: 'DELETE'
                    });
                    location.assign(listUrl(page));
                } catch (error) {
                    notify(error.message, true);
                } finally {
                    deleteButton.disabled = false;
                    $('#notice-edit').disabled = false;
                }
            });
        }

        if (noticeId) {
            loadDetail();
        } else {
            loadList();
        }
    })();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>

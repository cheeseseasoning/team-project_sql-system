<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>
<div class="page-heading"><h1>공지사항</h1><span class="caption">운영 및 이용 안내</span></div><section class="panel"><div class="table-scroll"><table><thead><tr><th>구분</th><th>제목</th><th>등록일</th></tr></thead><tbody id="notices-body"></tbody></table></div></section><article id="notice-detail" class="panel section-gap" hidden><span id="notice-category" class="caption"></span><h2 id="notice-title" class="section-gap"></h2><p id="notice-body" class="notice-body"></p><button id="close-notice" class="button">목록만 보기</button></article>
<script>
let notices=[];
function openNotice(id){const n=notices.find(x=>x.id===id);if(!n)return;$('#notice-category').textContent=n.category+' · '+n.date;$('#notice-title').textContent=n.title;$('#notice-body').textContent=n.body;$('#notice-detail').hidden=false;history.replaceState(null,'',BASE+'/notices?id='+encodeURIComponent(id));}
async function loadNotices(){try{notices=await api('/notices');$('#notices-body').innerHTML=notices.map(n=>'<tr><td>'+esc(n.category)+'</td><td><button class="table-link" data-notice="'+esc(n.id)+'">'+esc(n.title)+'</button></td><td>'+esc(n.date)+'</td></tr>').join('');document.querySelectorAll('[data-notice]').forEach(b=>b.addEventListener('click',()=>openNotice(b.dataset.notice)));openNotice(new URLSearchParams(location.search).get('id'));}catch(e){notify(e.message,true);}}
$('#close-notice').addEventListener('click',()=>{$('#notice-detail').hidden=true;history.replaceState(null,'',BASE+'/notices');});loadNotices();
</script>
<%@ include file="fragments/bottom.jspf" %>

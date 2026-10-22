<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>
<div class="page-heading"><div><p class="caption">DB팀 실행 권한 계정</p><h1>실행 목록</h1></div><a class="button" href="${pageContext.request.contextPath}/execution-results">실행 결과</a></div>
<section class="panel execution-queue-panel">
  <div class="panel-heading"><div><h2>승인된 요청</h2><p class="caption">최종 승인되어 실행 대기 중인 요청입니다.</p></div><button id="queue-refresh" class="button" type="button">새로고침</button></div>
  <form id="queue-search" class="queue-search"><label for="queue-keyword">요청 검색</label><input id="queue-keyword" type="search" placeholder="요청번호, 제목, 작성자"><button class="button primary" type="submit">검색</button></form>
  <div class="table-scroll"><table><thead><tr><th>요청 번호 / 제목</th><th>작성자</th><th>SQL 수</th><th>상태</th><th>제출 시각</th><th></th></tr></thead><tbody id="queue-rows"></tbody></table></div>
  <nav class="form-footer" aria-label="실행 목록 페이지 이동"><button id="queue-prev" class="button" type="button" disabled>이전</button><span id="queue-page" class="caption"></span><button id="queue-next" class="button" type="button" disabled>다음</button></nav>
</section>
<script>
(function(){
 const size=10; let page=1, keyword='', totalPages=0;
 const $id=id=>document.getElementById(id);
 const fmt=v=>v?new Date(v).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'}):'—';
 function empty(text){$id('queue-rows').innerHTML='<tr><td colspan="6" class="empty">'+esc(text)+'</td></tr>';}
 async function load(next=page){page=next;$id('queue-rows').setAttribute('aria-busy','true');empty('승인된 요청을 불러오는 중입니다.');
  try {const q=new URLSearchParams({page:String(page),size:String(size),keyword});const d=await api('/executions/ready?'+q);totalPages=d.totalPages||0;
   if(d.items.length){$id('queue-rows').innerHTML=d.items.map(r=>'<tr><td><a class="table-title" href="'+BASE+'/execution-queue/'+encodeURIComponent(r.requestId)+'">'+esc(r.requestNo)+'</a><span class="secondary">'+esc(r.title)+'</span></td><td>'+esc(r.drafterName)+'<span class="secondary">'+esc(r.deptName)+'</span></td><td class="numeric">'+esc(r.queryCount)+'</td><td>'+badge(r.batchStatus==='RUNNING'?'전체 실행 중':r.batchStatus==='SUCCESS'?'완료':r.batchStatus==='FAILED'?'실패':r.requestStatus==='RUNNING'?'개별 SQL 실행 중':r.batchId?'개별 실행 가능':'실행 대기',r.batchStatus==='RUNNING'||r.requestStatus==='RUNNING'?'waiting':r.batchStatus==='SUCCESS'?'success':'neutral')+'</td><td>'+esc(fmt(r.submittedAt))+'</td><td><a class="button" href="'+BASE+'/execution-queue/'+encodeURIComponent(r.requestId)+'">상세</a></td></tr>').join('');}
   else empty('실행 대기 중인 승인 요청이 없습니다.');
   $id('queue-page').textContent=totalPages?page+' / '+totalPages+' 페이지 · 총 '+d.totalCount+'건':'검색 결과 없음';
   $id('queue-prev').disabled=page<=1;$id('queue-next').disabled=page>=totalPages;
  } catch(e){empty(e.message);notify(e.message,true);} finally{$id('queue-rows').setAttribute('aria-busy','false');}
 }
 $id('queue-search').addEventListener('submit',e=>{e.preventDefault();keyword=$id('queue-keyword').value.trim();load(1);});
 $id('queue-refresh').addEventListener('click',()=>load());$id('queue-prev').addEventListener('click',()=>load(page-1));$id('queue-next').addEventListener('click',()=>load(page+1));load();
})();
</script>
<%@ include file="fragments/bottom.jspf" %>

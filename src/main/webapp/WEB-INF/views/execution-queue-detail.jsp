<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>
<div class="page-heading"><div><a class="back-link" href="${pageContext.request.contextPath}/execution-queue">← 실행 목록</a><h1 id="detail-title">실행 요청 상세</h1></div><button id="detail-refresh" class="button" type="button">상태 새로고침</button></div>
<section class="panel"><div class="panel-heading"><div><h2>실행 정보</h2><p class="caption">최종 승인된 요청의 저장 SQL을 서버에서 다시 검증한 뒤 실행합니다.</p></div><span id="detail-status" class="badge neutral">조회 중</span></div><dl id="request-info" class="queue-info"></dl><div id="batch-actions" class="execution-queue-actions"></div><p id="detail-message" class="execution-queue-message" role="status"></p></section>
<section class="panel section-gap"><div class="panel-heading"><div><h2>SQL 원문</h2><p class="caption">각 SQL을 개별 실행하거나 대기 상태인 전체 SQL을 한 번에 실행할 수 있습니다.</p></div><span id="sql-count" class="caption"></span></div><div id="sql-items" class="queue-sql-list"></div></section>
<script>
(function(){
 const requestId=${requestId}; let detail=null, timer=null, busyIds=new Set();
 const $id=id=>document.getElementById(id);
 const stateLabel={READY:'실행 대기',RUNNING:'전체 실행 중',INDIVIDUAL_RUNNING:'개별 SQL 실행 중',COMPLETED:'실행 완료',PARTIAL_FAILED:'일부 실패',FAILED:'실패',PENDING:'대기',SUCCESS:'성공'};
 const dt=v=>v?new Date(v).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'}):'—';
 function showMessage(s,error=false){$id('detail-message').textContent=s||'';$id('detail-message').classList.toggle('error-text',error);}
 function runButton(item){const canRun=detail.batchId&&detail.batchStatus==='PENDING'&&(detail.requestStatus==='READY'||detail.requestStatus==='RUNNING')&&item.executionStatus==='PENDING'&&!busyIds.has(item.executionId);return '<button type="button" class="button sql-run" data-execution="'+esc(item.executionId)+'" '+(canRun?'':'disabled')+'>SQL 실행</button>';}
 function render(d){detail=d;$id('detail-title').textContent=(d.requestNo||'요청')+' · '+(d.title||'제목 없음');$id('sql-count').textContent='SQL '+d.sqlItems.length+'건';
  const status=d.batchStatus==='PENDING'&&d.requestStatus==='RUNNING'?'INDIVIDUAL_RUNNING':d.batchStatus||'미등록';$id('detail-status').textContent=stateLabel[status]||status;$id('detail-status').className='badge '+(status==='SUCCESS'?'success':status==='RUNNING'||status==='INDIVIDUAL_RUNNING'?'waiting':status==='FAILED'||status==='PARTIAL_FAILED'?'danger':'neutral');
  $id('request-info').innerHTML='<div><dt>요청자</dt><dd>'+esc(d.drafterName)+' · '+esc(d.deptName)+'</dd></div><div><dt>요청 ID</dt><dd>'+esc(d.requestId)+'</dd></div><div><dt>요청 상태</dt><dd>'+esc(stateLabel[d.requestStatus]||d.requestStatus)+'</dd></div><div><dt>최종 승인</dt><dd>'+esc(d.approvalStatus)+'</dd></div><div><dt>제출 시각</dt><dd>'+esc(dt(d.submittedAt))+'</dd></div><div><dt>목적</dt><dd>'+esc(d.purpose||'—')+'</dd></div>';
  const canWhole=d.batchId&&d.batchStatus==='PENDING'&&d.requestStatus==='READY'&&d.sqlItems.length>0&&d.sqlItems.every(x=>x.executionStatus==='PENDING');
  let actions='';if(!d.batchId&&d.requestStatus==='READY')actions+='<button id="register-batch" class="button primary" type="button">실행 묶음 등록</button>';
  if(d.batchId)actions+='<button id="run-all" class="button primary" type="button" '+(canWhole?'':'disabled')+'>전체 SQL 실행</button>';
  actions+='<a class="button" href="'+BASE+'/execution-results">요청자 실행 결과 메뉴</a>';$id('batch-actions').innerHTML=actions;
  $id('sql-items').innerHTML=d.sqlItems.map(x=>{const executionLabel=x.executionStatus?(stateLabel[x.executionStatus]||x.executionStatus):(d.batchId?'실행 기록 없음':'실행 등록 전');return '<article class="queue-sql-card"><div class="panel-heading"><div><h3>'+esc(x.queryOrder)+'. '+esc(x.queryTitle||'SQL')+'</h3><span class="caption">실행 ID: '+esc(x.executionId||'미등록')+'</span></div>'+badge(executionLabel,x.executionStatus==='SUCCESS'?'success':x.executionStatus==='RUNNING'?'waiting':x.executionStatus==='FAILED'?'danger':'neutral')+'</div><pre><code>'+esc(x.sqlText)+'</code></pre><div class="queue-sql-footer">'+(x.errorMessage?'<p class="queue-error"><strong>실패 원인:</strong> '+esc(x.errorMessage)+'</p>':'')+runButton(x)+'</div></article>';}).join('')||'<p class="empty">등록된 SQL이 없습니다.</p>';
  if(d.batchStatus==='RUNNING'||d.sqlItems.some(x=>x.executionStatus==='RUNNING'))poll();
  bindActions();
 }
 function bindActions(){const reg=$id('register-batch');if(reg)reg.onclick=async()=>{reg.disabled=true;try{await post('/executions/requests/'+requestId+'/batches',{});showMessage('실행 묶음을 등록했습니다.');await load();}catch(e){showMessage(e.message,true);reg.disabled=false;}};
  const all=$id('run-all');if(all)all.onclick=async()=>{if(!confirm('이 요청에 등록된 SQL 전체를 실행할까요?'))return;all.disabled=true;try{await api('/executions/batches/'+detail.batchId+'/run',{method:'POST'});showMessage('전체 실행을 접수했습니다. 완료될 때까지 상태를 갱신합니다.');await load();poll();}catch(e){showMessage(e.message,true);all.disabled=false;}};
  document.querySelectorAll('.sql-run').forEach(b=>b.onclick=async()=>{const id=b.dataset.execution;if(!confirm('선택한 SQL을 실행할까요?'))return;b.disabled=true;busyIds.add(id);try{await api('/executions/batches/'+detail.batchId+'/executions/'+id+'/run',{method:'POST'});showMessage('SQL 실행을 접수했습니다.');await load();poll();}catch(e){showMessage(e.message,true);busyIds.delete(id);b.disabled=false;}});
 }
 async function load(){try{render(await api('/executions/requests/'+requestId));}catch(e){showMessage(e.message,true);}}
 function poll(){if(timer)return;timer=setTimeout(async()=>{timer=null;await load();if(detail&&(detail.batchStatus==='RUNNING'||detail.sqlItems.some(x=>x.executionStatus==='RUNNING')))poll();else busyIds.clear();},1500);}
 $id('detail-refresh').onclick=load;load();
})();
</script>
<%@ include file="fragments/bottom.jspf" %>

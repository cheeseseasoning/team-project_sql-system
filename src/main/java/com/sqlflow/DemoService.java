package com.sqlflow;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 시연 전용: 외부 AI/업무 DB에 접속하거나 입력 SQL을 실행하지 않습니다. */
@Service
public class DemoService {
    public static final String GENERAL_SQL="SELECT department_name, SUM(sales_amount) AS sales_total\nFROM V_SALES_SUMMARY\nWHERE sales_date >= '2026-09-01'\n  AND sales_date < '2026-10-01'\nGROUP BY department_name\nORDER BY sales_total DESC;";
    public static final String SENSITIVE_SQL="SELECT contract_id, customer_name, contract_amount\nFROM V_CONTRACT_DETAIL\nWHERE contract_date >= '2026-09-01'\n  AND contract_date < '2026-10-01';";
    public record Draft(String title,String purpose,List<String> sqls) {}
    public record Validation(boolean valid,String type,List<String> resources,List<String> errors,String message) {}
    public record Item(int number,String sql,String state,String error,List<Map<String,Object>> rows) {}
    public record Query(String id,String title,String purpose,String type,String state,String approval,List<Item> items,OffsetDateTime createdAt,OffsetDateTime expiresAt) {}
    public record Notice(String id,String category,String title,String date,String body) {}
    private final Map<String,Query> requests=new ConcurrentHashMap<>();
    private final AtomicInteger sequence=new AtomicInteger(12);
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneId.of("Asia/Seoul")); }
    private static final List<Map<String,Object>> ROWS=List.of(
        Map.of("department","영업 1팀","amount",128400000),Map.of("department","영업 2팀","amount",96800000),
        Map.of("department","온라인사업팀","amount",74200000),Map.of("department","해외사업팀","amount",52600000));
    public DemoService() {
        var n=now();
        requests.put("Q-20261014-012", new Query("Q-20261014-012","9월 부서별 매출 현황","월간 영업 실적 보고서 작성","GENERAL","COMPLETED","NOT_REQUIRED",List.of(new Item(1,GENERAL_SQL,"SUCCESS","",ROWS)),n,n.plusDays(30)));
        requests.put("Q-20261014-011", new Query("Q-20261014-011","계약 상세 현황 확인","계약별 실적 및 금액 확인","SENSITIVE","APPROVAL_PENDING","MANAGER_PENDING",List.of(new Item(1,SENSITIVE_SQL,"PENDING","",List.of())),n,n.plusDays(30)));
        requests.put("Q-20261013-008", new Query("Q-20261013-008","월간 영업 보고용 데이터","월간 영업 실적 보고서 작성","GENERAL","COMPLETED","NOT_REQUIRED",List.of(new Item(1,GENERAL_SQL,"SUCCESS","",ROWS),new Item(2,GENERAL_SQL,"FAILED","일시적 DB 연결 장애. DBA 허가 후 동일 SQL 최대 1회 재실행 대상입니다.",List.of())),n.minusDays(1),n.minusDays(1).plusDays(30)));
    }
    public List<Query> list(String keyword,String status) {
        String term=keyword==null?"":keyword.toLowerCase(Locale.ROOT);
        return requests.values().stream().filter(q->q.id().toLowerCase(Locale.ROOT).contains(term)||q.title().toLowerCase(Locale.ROOT).contains(term))
            .filter(q->status==null||status.isBlank()||q.state().equals(status)||status.equals("FAILED")&&q.items().stream().anyMatch(i->i.state().equals("FAILED")))
            .sorted(Comparator.comparing(Query::createdAt).reversed().thenComparing(Query::id,Comparator.reverseOrder())).toList();
    }
    public Query get(String id) { var q=requests.get(id);if(q==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"조회 요청을 찾을 수 없습니다.");return q; }
    public Map<String,Object> generate(String prompt) {
        if(prompt==null||prompt.isBlank()||prompt.length()>3000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"조회 요청을 1~3000자로 입력하세요.");
        if(prompt.contains("인사")||prompt.contains("직원")||prompt.contains("급여"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"현재 영업팀에 해당 데이터의 조회 권한이 없습니다.");
        boolean sensitive=prompt.contains("계약");
        if(!sensitive&&!prompt.contains("매출"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"예시 AI는 ‘9월 부서별 매출’ 또는 ‘9월 계약 상세’ 질문을 지원합니다.");
        return Map.of("sql",sensitive?SENSITIVE_SQL:GENERAL_SQL,"explanation",sensitive?"2026년 9월 계약 상세를 조회합니다. 민감 조회로 결재가 필요합니다.":"2026년 9월 매출을 부서별로 합산합니다. 일반 조회입니다.","demo",true);
    }
    public Validation validate(Draft d) {
        List<String> errors=new ArrayList<>();Set<String> resources=new LinkedHashSet<>();boolean sensitive=false;
        if(d.title()==null||d.title().isBlank()||d.title().length()>120)errors.add("요청 제목은 1~120자로 입력하세요.");
        if(d.purpose()==null||d.purpose().isBlank()||d.purpose().length()>1000)errors.add("조회 목적은 1~1000자로 입력하세요.");
        if(d.sqls()==null||d.sqls().isEmpty()||d.sqls().size()>5)errors.add("SQL은 1개 이상 5개 이하로 등록하세요.");
        else for(int n=0;n<d.sqls().size();n++) {
            String s=d.sqls().get(n);String normalized=normalize(s);
            if(normalized.equals(normalize(GENERAL_SQL)))resources.add("V_SALES_SUMMARY");
            else if(normalized.equals(normalize(SENSITIVE_SQL))){resources.add("V_CONTRACT_DETAIL");sensitive=true;}
            else errors.add("SQL "+(n+1)+": 시연 환경은 제공된 매출 요약·계약 상세 SELECT 예시만 허용합니다. 실제 SQL 파서/권한 검증기는 연결 전입니다.");
        }
        return new Validation(errors.isEmpty(),sensitive?"SENSITIVE":"GENERAL",List.copyOf(resources),errors,sensitive?"민감 조회 · 중간 결재자 → 부서장 승인 후 실행":"일반 조회 · 결재 없이 자동 실행");
    }
    private String normalize(String s) { return s==null?"":s.strip().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT); }
    public Query submit(Draft d) {
        var v=validate(d);if(!v.valid())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,String.join(" ",v.errors()));
        String id="Q-"+now().toLocalDate().toString().replace("-","")+"-"+String.format("%03d",sequence.incrementAndGet());
        boolean s=v.type().equals("SENSITIVE");var items=new ArrayList<Item>();
        for(String sql:List.copyOf(d.sqls()))items.add(new Item(items.size()+1,sql,s?"PENDING":"SUCCESS","",s?List.of():ROWS));
        var n=now();var q=new Query(id,d.title().strip(),d.purpose().strip(),v.type(),s?"APPROVAL_PENDING":"COMPLETED",s?"MANAGER_PENDING":"NOT_REQUIRED",List.copyOf(items),n,n.plusDays(30));requests.put(id,q);return q;
    }
    public Item result(String id,int number) {
        var q=get(id);if(!q.state().equals("COMPLETED"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"결재 및 실행 완료 후 결과를 확인할 수 있습니다.");
        if(!q.expiresAt().isAfter(now()))throw new ResponseStatusException(HttpStatus.GONE,"결과 파일 보관 기간이 만료되었습니다.");
        var i=q.items().stream().filter(x->x.number()==number).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"SQL 항목이 없습니다."));
        if(!i.state().equals("SUCCESS"))throw new ResponseStatusException(HttpStatus.CONFLICT,"정상 생성된 결과가 없습니다.");return i;
    }
    public List<Notice> notices() { return List.of(
        new Notice("retention","이용 안내","조회 결과 파일 보관 및 다운로드 안내","2026.10.14","조회 결과 파일은 생성일로부터 30일 동안 보관합니다. 화면에서는 SQL별 최대 100행을 미리보기로 제공합니다. 정상 완료된 전체 결과는 CSV로 다운로드할 수 있습니다. 실제 서비스에서는 접근 시 현재 VIEW 권한을 재검증해야 합니다."),
        new Notice("approval","업무 안내","민감 데이터 조회 요청 결재 절차 안내","2026.10.13","일반 조회는 검증과 제출 후 별도 결재 없이 실행합니다. 민감 조회는 중간 결재자와 부서장의 순차 승인 후 실행합니다. 조회 권한이 없는 VIEW는 결재로 접근 제한을 우회할 수 없습니다."),
        new Notice("guide","사용 안내","조회 요청 작성 및 제출 시 유의사항","2026.10.12","요청당 SELECT SQL은 1~5개입니다. 제출 후에는 SQL 원문을 수정할 수 없습니다. 변경이 필요하면 기존 요청을 보존한 채 신규 요청으로 작성합니다. 이 시스템은 현재 고정 사용자·메모리 데이터 기반 시연 환경입니다.")); }
}

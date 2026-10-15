package com.sqlflow;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DemoService service;
    public ApiController(DemoService service){this.service=service;}
    @GetMapping("/dashboard") public Map<String,Object> dashboard(){var q=service.list("","");return Map.of("user",Map.of("name","김업무","department","영업팀","position","사원"),"total",q.size(),"pending",q.stream().filter(x->x.state().equals("APPROVAL_PENDING")).count(),"completed",q.stream().filter(x->x.state().equals("COMPLETED")).count(),"successSql",q.stream().flatMap(x->x.items().stream()).filter(i->i.state().equals("SUCCESS")).count(),"failedSql",q.stream().flatMap(x->x.items().stream()).filter(i->i.state().equals("FAILED")).count(),"recent",q.stream().limit(5).toList(),"notices",service.notices(),"demo",true);}
    @GetMapping("/requests") public List<DemoService.Query> requests(@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status){return service.list(keyword,status);}
    @GetMapping("/requests/{id}") public DemoService.Query request(@PathVariable String id){return service.get(id);}
    @PostMapping("/sql/generate") public Map<String,Object> generate(@RequestBody Map<String,String> input){return service.generate(input.get("prompt"));}
    @PostMapping("/sql/validate") public DemoService.Validation validate(@RequestBody DemoService.Draft input){return service.validate(input);}
    @PostMapping("/requests") public ResponseEntity<DemoService.Query> submit(@RequestBody DemoService.Draft input){return ResponseEntity.status(201).body(service.submit(input));}
    @GetMapping("/requests/{id}/results/{number}") public Map<String,Object> result(@PathVariable String id,@PathVariable int number){var i=service.result(id,number);return Map.of("rows",i.rows().stream().limit(100).toList(),"total",i.rows().size(),"limit",100);}
    @GetMapping(value="/requests/{id}/results/{number}/csv",produces="text/csv;charset=UTF-8") public ResponseEntity<byte[]> csv(@PathVariable String id,@PathVariable int number){var i=service.result(id,number);StringBuilder csv=new StringBuilder("\uFEFF부서,매출 합계\r\n");for(var row:i.rows())csv.append(row.get("department")).append(',').append(row.get("amount")).append("\r\n");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=sqlflow-"+number+".csv").body(csv.toString().getBytes(StandardCharsets.UTF_8));}
    @GetMapping("/notices") public List<DemoService.Notice> notices(){return service.notices();}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> error(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(ex.getReason(),"요청을 처리할 수 없습니다.")));}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) public ResponseEntity<Map<String,String>> malformed(){return ResponseEntity.badRequest().body(Map.of("message","요청 데이터 형식을 확인하세요."));}
}

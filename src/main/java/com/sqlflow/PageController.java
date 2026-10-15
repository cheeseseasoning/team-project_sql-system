package com.sqlflow;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
    @ModelAttribute public void common(Model model) { model.addAttribute("userName", "김업무"); }
    @GetMapping("/") public String home(Model model) { return view(model,"home","업무 현황"); }
    @GetMapping("/requests/new") public String create(Model model, @RequestParam(defaultValue="ai") String mode) {
        model.addAttribute("mode", "direct".equals(mode)?"direct":"ai"); return view(model,"request-form","조회 요청 작성");
    }
    @GetMapping("/requests/review") public String review(Model model) { return view(model,"request-review","SQL 확인 및 제출"); }
    @GetMapping("/requests") public String list(Model model) { return view(model,"requests","내 조회 요청"); }
    @GetMapping("/requests/{id}") public String detail(Model model,@PathVariable String id) {
        model.addAttribute("requestId",id); return view(model,"request-detail","조회 요청 상세");
    }
    @GetMapping("/notices") public String notices(Model model) { return view(model,"notices","공지사항"); }
    private String view(Model m,String page,String title) {
        m.addAttribute("page",page); m.addAttribute("pageTitle",title);
        m.addAttribute("activeMenu",page.equals("home")?"home":page.equals("notices")?"notices":page.equals("request-form")||page.equals("request-review")?"create":"requests");
        return page;
    }
}

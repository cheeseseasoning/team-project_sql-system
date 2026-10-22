package com.sqlflow;

import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.sqlflow.auth.security.SqlFlowUserDetails;

@Controller
public class PageController {

	private final Environment environment;

    public PageController(Environment environment) {
        this.environment = environment;
    }
	
	@ModelAttribute
	public void common(Model model, Authentication authentication) {
		if (authentication != null
				&& authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)
				&& authentication.getPrincipal() instanceof SqlFlowUserDetails user) {
			model.addAttribute("userName", user.getDisplayName());
			model.addAttribute("deptName", user.getDeptName());
			model.addAttribute("positionName", user.getPositionName());
			model.addAttribute("authenticated", true);
		} else {
			model.addAttribute("userName", "방문자");
			model.addAttribute("deptName", "");
			model.addAttribute("positionName", "");
			model.addAttribute("authenticated", false);
		}
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/")
	public String home(Model model) {
		return view(model, "home", "업무 현황");
	}

	@GetMapping("/requests/new")
	public String create(Model model,
			@RequestParam(name = "mode", defaultValue = "direct") String mode) {
		model.addAttribute("mode", "ai".equals(mode) ? "ai" : "direct");
		return view(model, "request-form", "조회 요청 작성");
	}

	@GetMapping("/requests/review")
	public String review() {
		return "redirect:/requests/new";
	}

	@GetMapping("/requests")
	public String list(Model model) {
		return view(model, "requests", "내 조회 요청");
	}

	@GetMapping("/requests/{id}")
	public String detail(Model model, @PathVariable String id) {
		model.addAttribute("requestId", id);
		return view(model, "request-detail", "조회 요청 상세");
	}

	@GetMapping("/requests/{id}/edit")
	public String editDraft(Model model, @PathVariable Long id) {
		model.addAttribute("requestId", id);
		return view(model, "request-edit", "조회 요청 초안 수정");
	}

	@GetMapping("/approvals/pending")
	public String pendingApprovals(Model model) {
		return view(model, "approval-pending", "결재 대기 목록");
	}

	@GetMapping("/approvals/{id}")
	public String approvalDetail(Model model, @PathVariable Long id) {
		model.addAttribute("requestId", id);
		return view(model, "approval-detail", "결재 요청 상세");
	}
	
	@GetMapping("/execution-results")
	public String executionResults(Model model) {
	    return view(model, "execution-results", "실행 결과");
	}
	
	@GetMapping("/execution-results/{executionId}")
	public String executionResultDetail(
	        Model model,
	        @PathVariable("executionId") Long executionId) {

	    model.addAttribute("executionId", executionId);

	    model.addAttribute(
	            "csvPreviewEnabled",
	            environment.matchesProfiles("csv-preview")
	    );

	    return view(
	            model,
	            "execution-result-detail",
	            "SQL 실행 결과 상세"
	    );
	}

	@GetMapping("/notices")
	public String notices(Model model) {
		return view(model, "notices", "공지사항");
	}

	private String view(Model model, String page, String title) {
		model.addAttribute("page", page);
		model.addAttribute("pageTitle", title);
		model.addAttribute("activeMenu",
		        page.equals("home") ? "home"
		                : page.equals("notices") ? "notices"
		                : page.startsWith("execution-result") ? "execution-results"
		                : page.startsWith("approval-") ? "approvals"
		                : page.equals("request-form") || page.equals("request-review")
		                        ? "create" : "requests");
		return page;
	}
}

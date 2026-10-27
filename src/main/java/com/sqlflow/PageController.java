package com.sqlflow;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;

@Controller
public class PageController {
	
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

	@GetMapping("/admin/users/new")
	public String createUser(Model model) {
		return view(model, "admin-user-form", "계정 생성·역할 부여");
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
	public String executionResults(Model model, @RequestParam(name="view", required=false) String view) {
	    if ("queue".equals(view)) return "redirect:/execution-queue";
	    return view(model, "execution-results", "실행 결과");
	}

	@GetMapping("/execution-queue")
	public String executionQueue(Model model, Authentication authentication) {
	    requireDbApprover(authentication);
	    return view(model, "execution-queue", "실행 목록");
	}

	@GetMapping("/execution-queue/{requestId}")
	public String executionQueueDetail(Model model, @PathVariable Long requestId, Authentication authentication) {
	    requireDbApprover(authentication);
	    model.addAttribute("requestId", requestId);
	    return view(model, "execution-queue-detail", "실행 요청 상세");
	}

	private void requireDbApprover(Authentication authentication) {
	    if (authentication == null || !(authentication.getPrincipal() instanceof SqlFlowUserDetails user)
	            || !user.isEnabled() || user.getAuthorities().stream().noneMatch(a -> "ROLE_DB_APPROVER".equals(a.getAuthority()))) {
	        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DB팀 실행 권한이 필요합니다.");
	    }
	}
	
	@GetMapping("/execution-results/{executionId}")
	public String executionResultDetail(
	        Model model,
	        @PathVariable("executionId") Long executionId) {

	    model.addAttribute("executionId", executionId);

	    return view(
	            model,
	            "execution-result-detail",
	            "SQL 실행 결과 상세"
	    );
	}
	
	/** 읽기 전용 뷰 목록 화면을 연다. */
	@GetMapping("/views")
	public String viewList(Model model) {
	    return view(model, "views", "뷰 조회");
	}

	@GetMapping("/notices")
	public String notices(Model model) {
		return view(model, "notices", "공지사항");
	}
	
	/** 뷰 상세 화면을 연다. 실제 데이터는 상세 API에서 조회한다. */
	@GetMapping("/views/detail")
	public String viewDetail(Model model) {
	    return view(model, "view-detail", "뷰 상세");
	}

	private String view(Model model, String page, String title) {
		model.addAttribute("page", page);
		model.addAttribute("pageTitle", title);
		model.addAttribute("activeMenu",
		        page.equals("home") ? "home"
		                : page.equals("notices") ? "notices"
		                : page.equals("views") || page.equals("view-detail") ? "views"		
		                : page.equals("admin-user-form") ? "admin-users"
		                : page.startsWith("execution-queue") ? "execution-queue"
		                : page.startsWith("execution-result") ? "execution-results"
		                : page.startsWith("approval-") ? "approvals"
		                : page.equals("request-form") || page.equals("request-review")
		                        ? "create" : "requests");
		return page;
	}
}

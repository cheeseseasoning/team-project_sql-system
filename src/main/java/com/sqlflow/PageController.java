package com.sqlflow;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.sqlflow.access.service.ResourcePolicyAdminService;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.AccountService;
import com.sqlflow.execution.service.ExecutionQueueService;

@Controller
public class PageController {

    private final ResourcePolicyAdminService resourcePolicyAdminService;
    private final AccountService accountService;
    private final ExecutionQueueService executionQueueService;

    public PageController(
            ResourcePolicyAdminService resourcePolicyAdminService,
            AccountService accountService,
            ExecutionQueueService executionQueueService) {
        this.resourcePolicyAdminService = resourcePolicyAdminService;
        this.accountService = accountService;
        this.executionQueueService = executionQueueService;
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
        return "account/login";
    }

    @GetMapping("/admin/users/new")
    public String createUser(
            Model model,
            Authentication authentication) {
        accountService.assertActiveSystemAdmin(loginId(authentication));
        return view(model, "admin-user-form", "계정 생성");
    }

    @GetMapping("/admin/users/{userId}")
    public String accountDetail(
            Model model,
            @PathVariable Long userId,
            Authentication authentication) {
        accountService.assertActiveSystemAdmin(loginId(authentication));
        model.addAttribute("userId", userId);
        return view(model, "admin-user-detail", "계정 상세");
    }

    @GetMapping("/accounts")
    public String accountList(Model model) {
        return view(model, "accounts", "계정 목록");
    }

    @GetMapping("/")
    public String home(Model model) {
        return view(model, "home", "Home");
    }

    @GetMapping("/my-page")
    public String myPage(Model model) {
        return view(model, "my-page", "마이페이지");
    }

    @GetMapping("/requests/new")
    public String create(
            Model model,
            @RequestParam(name = "mode", defaultValue = "direct") String mode) {
        model.addAttribute("mode", "ai".equals(mode) ? "ai" : "direct");
        return view(model, "request-form", "데이터 조회 요청 작성");
    }

    @GetMapping("/requests/review")
    public String review() {
        return "redirect:/requests/new";
    }

    @GetMapping("/requests")
    public String list(Model model) {
        return view(model, "requests", "내 요청함");
    }

    @GetMapping("/requests/{id}")
    public String detail(Model model, @PathVariable String id) {
        model.addAttribute("requestId", id);
        return view(model, "request-detail", "기안서 상세");
    }

    @GetMapping("/requests/{id}/edit")
    public String editDraft(Model model, @PathVariable("id") Long id) {
        model.addAttribute("requestId", id);
        model.addAttribute("redraftMode", false);
        return view(model, "request-edit", "데이터 조회 요청 초안 수정");
    }

    @GetMapping("/requests/{id}/redraft")
    public String redraftForm(Model model, @PathVariable("id") Long id) {
        model.addAttribute("requestId", id);
        model.addAttribute("redraftMode", true);
        return view(model, "request-edit", "데이터 조회 요청 재기안");
    }

    @GetMapping("/department-requests")
    public String departmentRequests(Model model) {
        return view(model, "department-requests", "부서 요청함");
    }

    @GetMapping("/department-requests/{requestId}")
    public String departmentRequestDetail(
            Model model,
            @PathVariable("requestId") Long requestId) {
        model.addAttribute("requestId", requestId);
        return view(model, "department-request-detail", "기안서 상세");
    }

    @GetMapping("/approvals/pending")
    public String pendingApprovals(Model model) {
    	return view(model, "approval-pending", "받은 결재");
    }

    @GetMapping("/approvals/{id}")
    public String approvalDetail(Model model, @PathVariable Long id) {
        model.addAttribute("requestId", id);
        return view(model, "approval-detail", "기안서 상세");
    }

    @GetMapping("/execution-results")
    public String executionResults(
            Model model,
            @RequestParam(name = "view", required = false) String view) {
        if ("queue".equals(view)) {
            return "redirect:/execution-queue";
        }
        return view(model, "execution-results", "쿼리 실행 결과");
    }

    @GetMapping("/execution-queue")
    public String executionQueue(
            Model model,
            Authentication authentication) {
        executionQueueService.assertCanAccess(principal(authentication));
        return view(model, "execution-queue", "쿼리 실행 대기");
    }

    @GetMapping("/execution-queue/{requestId}")
    public String executionQueueDetail(
            Model model,
            @PathVariable Long requestId,
            Authentication authentication) {
        executionQueueService.assertCanAccess(principal(authentication));
        model.addAttribute("requestId", requestId);
        return view(model, "execution-queue-detail", "기안서 상세");
    }

    @GetMapping("/execution-results/{executionId}")
    public String executionResultDetail(
            Model model,
            @PathVariable("executionId") Long executionId) {
        model.addAttribute("executionId", executionId);
        return view(model, "execution-result-detail", "쿼리 실행 결과 상세");
    }

    @GetMapping("/view-create-requests/new")
    public String createViewRequest(Model model) {
        model.addAttribute("dbChangeFormMode", "create");
        return view(model, "view-create-request-form", "데이터 변경 요청 작성");
    }

    @GetMapping("/view-create-requests/{requestId}/edit")
    public String editViewRequest(Model model, @PathVariable Long requestId) {
        model.addAttribute("dbChangeFormMode", "edit");
        model.addAttribute("requestId", requestId);
        return view(model, "view-create-request-form", "데이터 변경 요청 초안 수정");
    }

    @GetMapping("/view-create-requests/{requestId}/redraft")
    public String redraftViewRequest(Model model, @PathVariable Long requestId) {
        model.addAttribute("dbChangeFormMode", "redraft");
        model.addAttribute("requestId", requestId);
        return view(model, "view-create-request-form", "데이터 변경 요청 재기안");
    }

    /** 읽기 전용 뷰 목록 화면을 연다. */
    @GetMapping("/views")
    public String viewList(Model model) {
        return view(model, "views", "뷰 조회");
    }

    @GetMapping("/db-admin/resources")
    public String resourcePolicyAdmin(
            Model model,
            Authentication authentication) {
        Long userId = userId(authentication);
        resourcePolicyAdminService.assertCanView(userId);
        return view(model, "db-resource-admin", "리소스·접근 정책 관리");
    }

    @GetMapping("/db-admin/resources/{resourceId}")
    public String resourcePolicyDetail(
            Model model,
            Authentication authentication,
            @PathVariable Long resourceId) {
        Long userId = userId(authentication);
        resourcePolicyAdminService.assertCanView(userId);
        model.addAttribute("resourceId", resourceId);
        return view(model, "db-resource-admin", "리소스·접근 정책 상세");
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

    private String loginId(Authentication authentication) {
        SqlFlowUserDetails user = principal(authentication);
        return user == null ? null : user.getLoginId();
    }

    private Long userId(Authentication authentication) {
        SqlFlowUserDetails user = principal(authentication);
        return user == null ? null : user.getUserId();
    }

    private SqlFlowUserDetails principal(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof SqlFlowUserDetails user) {
            return user;
        }
        return null;
    }

    private String view(Model model, String page, String title) {
        model.addAttribute("page", page);
        model.addAttribute("pageTitle", title);
        String activeMenu =
                page.equals("home") ? "home"
                        : page.equals("my-page") ? "my-page"
                        : page.equals("accounts") ? "accounts"
                        : page.equals("view-create-request-form")
                                ? ("create".equals(model.getAttribute("dbChangeFormMode"))
                                        ? "view-create-requests" : "requests")
                        : page.startsWith("department-request") ? "department-requests"
                        : page.equals("notices") ? "notices"
                        : page.equals("views") || page.equals("view-detail") ? "views"
                        : page.equals("db-resource-admin") ? "db-resource-admin"
                        : page.startsWith("admin-user") ? "admin-users"
                        : page.startsWith("execution-queue") ? "execution-queue"
                        : page.startsWith("execution-result") ? "execution-results"
                        : page.startsWith("approval-") ? "approvals"
                        : page.equals("request-form") || page.equals("request-review")
                                ? "create" : "requests";

        model.addAttribute("activeMenu", activeMenu);
        addBreadcrumb(model, activeMenu, title);
        
        return switch (page) {
            case "home" -> "home/" + page;
            case "my-page" -> "account/" + page;
            case "accounts", "admin-user-detail", "admin-user-form", "db-resource-admin" -> "admin/" + page;
            case "requests", "request-form", "request-edit", "request-detail", "view-create-request-form" -> "request/" + page;
            case "department-requests", "department-request-detail" -> "department/" + page;
            case "approval-pending", "approval-detail" -> "approval/" + page;
            case "execution-queue", "execution-queue-detail", "execution-results", "execution-result-detail" -> "execution/" + page;
            case "views", "view-detail" -> "view-catalog/" + page;
            case "notices" -> "notice/" + page;
            default -> page;
        };
    }
    
    private void addBreadcrumb(Model model, String activeMenu, String title) {
        MenuPath menu = switch (activeMenu) {
	        case "views" ->
			        new MenuPath("데이터 조회·변경", "뷰 조회", "/views");
			case "create" ->
			        new MenuPath("데이터 조회·변경", "데이터 조회 요청", "/requests/new");
			case "view-create-requests" ->
			        new MenuPath("데이터 조회·변경", "데이터 변경 요청",
			                "/view-create-requests/new");
            case "requests" ->
                    new MenuPath("기안·결재", "내 요청함", "/requests");
            case "department-requests" ->
                    new MenuPath("기안·결재", "부서 요청함", "/department-requests");
            case "approvals" ->
                    new MenuPath("기안·결재", "받은 결재", "/approvals/pending");
            case "execution-results" ->
                    new MenuPath(null, "쿼리 실행 결과", "/execution-results");
            case "execution-queue" ->
                    new MenuPath("DB 운영", "쿼리 실행 대기", "/execution-queue");
            case "db-resource-admin" ->
                    new MenuPath("DB 운영", "리소스·접근 정책 관리", "/db-admin/resources");
            case "accounts", "admin-users" ->
                    new MenuPath(null, "계정 목록", "/accounts");
            case "notices" ->
                    new MenuPath(null, "공지사항", "/notices");
            case "my-page" ->
                    new MenuPath(null, "마이페이지", "/my-page");
            default -> null;
        };

        String current = title;

        if (menu != null) {
            model.addAttribute("breadcrumbGroup", menu.group());

            if (!menu.label().equals(title)) {
                model.addAttribute("breadcrumbMenuLabel", menu.label());
                model.addAttribute("breadcrumbMenuPath", menu.path());

                String prefix = menu.label() + " ";
                if (title.startsWith(prefix)) {
                    current = title.substring(prefix.length());
                }
            }
        }

        model.addAttribute("breadcrumbCurrent", current);
    }

    private record MenuPath(String group, String label, String path) {
    }
}

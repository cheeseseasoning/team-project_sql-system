# SQLFlow — JSP 업무 포털

현재 피그마의 메인페이지와 클릭 흐름을 Spring Boot + JSP + JavaScript fetch로 구현했습니다.
JavaScript는 JSP 내부에 있으며 공통 JavaScript는 JSP 조각 `fragments/top.jspf` 안에 있습니다. 외부 JavaScript 파일이나 CDN은 사용하지 않습니다. CSS는 `src/main/resources/static/css/sqlflow.css`에 분리했습니다. 아이콘도 내부 정적 파일입니다.

## 실행

Windows PowerShell에서 프로젝트 폴더를 열고:

```powershell
.\run.ps1
```

브라우저에서 http://localhost:8080 으로 접속합니다. 이 작업 공간에는 `.tools`에 JDK 17과 Maven을 준비했습니다. 다른 PC에서 이 폴더를 가져갔고 도구가 없다면 인터넷 사용 가능한 개발 환경에서 먼저 `./setup.ps1`을 실행하세요. 시스템 JDK 17 이상과 Maven이 있으면 `mvn spring-boot:run`도 가능합니다.

```powershell
.\maven.ps1 test
.\maven.ps1 clean package
java -jar target/sqlflow-0.0.1-SNAPSHOT.war
```

JSP 지원을 위해 실행 가능한 WAR로 구성했습니다. 기본 포트는 `application.properties`의 `server.port`로 변경합니다.

`run.ps1`의 기본 포트는 8080입니다. 다른 포트는 `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Port 8081`로 실행합니다. 이미 SQLFlow가 실행 중이면 새 빌드를 시도하지 않고 접속 주소를 표시합니다. 수정 사항을 반영하려면 기존 서버 터미널에서 Ctrl+C로 종료한 뒤 다시 실행하세요. Windows에서는 실행 중인 WAR 파일을 덮어쓸 수 없어 빌드가 실패할 수 있습니다. `-BuildOnly`는 서버 시작 없이 빌드만 검증합니다.

## 화면

| 경로 | JSP | 기능 |
| --- | --- | --- |
| `/` | home.jsp | 개인 현황, 바로가기, 최근 요청, 결재 진행, 공지 |
| `/requests/new` | request-form.jsp | 자연어 요청, SQL 초안 생성, SQL 1~5개 등록 |
| `/requests/new?mode=direct` | request-form.jsp | 직접 SQL 작성 |
| `/requests/review` | request-review.jsp | 검증 결과 확인, 제출 |
| `/requests` | requests.jsp | 제목·요청번호 및 상태 검색 |
| `/requests/{id}` | request-detail.jsp | 성공·결재 대기·일부 실패, SQL 원문, 결과와 CSV |
| `/approvals/pending` | approval-pending.jsp | 로그인 사용자에게 배정된 현재 결재 대기 요청 |
| `/approvals/{id}` | approval-detail.jsp | 배정된 요청과 제출 SQL 검토 및 승인 |
| `/notices` | notices.jsp | 공지 목록 및 상세 |
| `/login` | login.jsp | 활성 계정 로그인 및 세션 생성 |

뷰 생성 요청은 `/view-create-requests/new`에서 작성합니다. 초안은 `QUERY_REQUEST.REQUEST_TYPE='CREATE_VIEW'`와 `VIEW_CREATE_SQL`에 저장하고, 제출 후 기존 `APPROVAL` 결재선을 사용합니다. DB팀 결재자의 최종 승인 뒤 부서 기안 상세에서 `.sql` 파일을 내려받고, 외부 처리 결과를 등록할 수 있습니다. 처리 결과는 요청 상세에서 요청자와 부서 구성원이 확인합니다. 배포 전에 관리 Oracle DB에 `db/manual/V004__view_create_request.sql`, `db/manual/V005__view_create_result.sql`을 순서대로 적용해야 합니다. 뷰 생성 요청은 일반 조회 실행 대기열에 등록되지 않습니다.

공통 헤더·사이드바는 `WEB-INF/views/fragments/top.jspf`, 하단은 `bottom.jspf`입니다. 로그인은 Spring Security 폼 인증과 서버 세션으로 처리하며, `USERS.LOGIN_ID`, BCrypt 형식의 `PASSWORD_HASH`, `ENABLED='Y'` 계정을 사용합니다. 요청자 ID와 부서는 로그인 계정에서 가져옵니다. 로그아웃과 변경 요청에 CSRF 토큰을 적용합니다. 화면과 요청 날짜는 한국 시간 기준입니다.

## 시연 순서

1. 메인 → AI 조회 요청. 제목과 목적을 입력합니다.
2. `2026년 9월 부서별 매출 합계를 보여줘` 입력 → SQL 초안 생성.
3. SQL 검증 및 다음 단계 → 요청 제출 → 결과 미리보기와 실제 예시 CSV 다운로드.
4. `계약 상세` 예시로 작성하면 민감 조회로 분류되어 결재 대기하며 결과 접근은 차단됩니다.
5. 메인의 월간 영업 보고 요청을 열면 SQL 한 개 성공·한 개 실패를 확인할 수 있습니다.
6. 결과 화면의 ‘신규 요청으로 작성’은 기존 SQL을 변경하지 않고 작성 화면으로 복사합니다.

초안은 현재 브라우저 탭의 sessionStorage에 보관됩니다. 요청은 서버 메모리에 저장되어 재시작 시 초기화됩니다. 시연 API는 임의 SQL을 실행하지 않고 지정한 두 SELECT 템플릿만 허용합니다. SQL 문법·접근 정책의 실제 검증기로 사용할 수 있는 구현은 아닙니다.

## API와 실제 시스템 연동 지점

- `GET /api/dashboard`, `GET /api/requests?keyword=&status=`, `GET /api/requests/{id}`
- `POST /api/sql/generate` — 현재 매출/계약 키워드 기반 고정 SQL. 내부 LLM 호출로 교체합니다.
- `POST /api/sql/validate` — 현재 템플릿 일치 검증. 실제 DBMS용 SQL 파서, 모든 VIEW·컬럼 검증, 조직 정책으로 교체합니다.
- `POST /api/requests` — 요청 등록과 일반/민감 분류. 관리 DB와 실행·결재 서비스로 분리합니다.
- `GET /api/requests/{id}/approval-candidates` — 초안 작성자의 부서장과 DB팀 결재 후보 조회.
- `POST /api/requests/{id}/submit` — 본인 초안을 검증하고 부서장 → DB팀 순서로 결재선을 생성합니다.
- `GET /api/approvals/pending` — 로그인 사용자의 활성 직급·부서·역할과 현재 결재 배정을 확인해 처리 가능한 요청만 반환합니다.
- `GET /api/approvals/{requestId}` — 현재 결재자로 배정된 사용자에게 요청과 제출 SQL을 반환합니다.
- `POST /api/approvals/{id}/approve` — 현재 결재 순서에 배정된 결재자만 승인할 수 있습니다. 부서장은 요청 부서의 활성 DIRECTOR여야 하고, DB팀 최종 결재자는 활성 DB팀 사용자이며 DB_APPROVER 역할이 있어야 합니다. 최종 승인 시 `APPROVAL_STATUS=APPROVED`, `REQUEST_STATUS=READY`로 함께 변경합니다.
- `GET /api/requests/{id}/results/{number}`, `GET /api/requests/{id}/results/{number}/csv`
- `GET /api/notices`

`DemoService`는 고정 사용자와 메모리 예시 데이터만 사용합니다. 시연 API의 요청·결과·상태는 메모리 데이터이며, 실제 AI·조회 DB·SSO·DBA 재실행 승인·감사 로그·결과 파일 저장/정리는 연결되어 있지 않습니다. 초안 CRUD와 결재 후보 조회·제출 및 배정 결재 승인은 Oracle 연동 API를 사용합니다.

초안 제출 API는 Oracle `APPROVAL` 테이블을 사용합니다. DB팀 결재 후보를 노출하려면 `ROLE`에 `ROLE_CODE = 'DB_APPROVER'` 역할을 등록하고, 후보가 될 활성 DB팀 사용자에게 `USER_ROLE`로 부여해야 합니다. 부서장 후보는 요청 당시 부서의 활성 `DIRECTOR` 직급 사용자로 판정합니다. ERD상 한 부서에 부장이 여러 명이면 현재 API의 단일 부서장 응답으로 확정할 수 없어 제출을 차단합니다.

현재 확정된 직급·부서 권한은 다음과 같습니다. 일반 사용자는 본인이 작성한 요청만 조회·수정·삭제할 수 있고, 부서장은 요청 부서의 활성 `DIRECTOR` 직급일 때 배정된 1단계 결재를 처리합니다. 최종 결재는 활성 DB팀 `DB_APPROVER`에게만 배정하며, 실행 대기 묶음 등록도 활성 DB팀 사용자로 제한합니다. 결재 승인은 현재 배정된 결재선과 계정 상태를 DB에서 다시 확인합니다. 별도의 부서 단위 요청 열람 권한이나 `DB_ADMIN`의 업무 권한은 정책이 정해지지 않아 부여하지 않습니다.

시작 전에 `USERS.PASSWORD_HASH`에 BCrypt로 해시한 비밀번호를 저장하고, 사용자 계정을 `ENABLED='Y'`로 설정하세요. 계정 발급·비밀번호 재설정 화면은 구현 범위에 포함하지 않았습니다. 요청 CRUD와 결재 후보 조회·제출은 로그인 사용자와 작성자 ID를 대조하고, 결재 대기 목록·상세 및 승인 시 현재 결재 배정·활성 계정·직급·부서를 재검증합니다. 반려·결재 이력, 결과별 업무 권한과 감사 이력, 제출 SQL 불변성, 읽기 전용 DB 계정, 실행 제한과 30일 보관 정책은 추가 구현 범위입니다.

## 내부망 배포

런타임에 외부 API·CDN을 요청하지 않습니다. Figma의 Noto Sans KR 폰트를 `static/fonts`에 포함했으며 `OFL.txt` 라이선스도 함께 보관합니다. CSS의 `@font-face`가 내부 서버에서 글꼴을 제공합니다. 빌드 의존성과 JDK는 승인된 반입 절차로 내부망에 준비합니다. 외부 AI가 아닌 내부 LLM 연결은 별도 백엔드 작업입니다.

디자인 원본: https://www.figma.com/design/rIEPy29qllAn2arsZR0GGr?node-id=11-56
피그마 호출 한도로 인해, 이번 코드 구현은 대화에서 생성한 Figma 구성 코드와 확인된 메인 캡처 및 프로토타입 정의를 기준으로 작성했습니다. 별도의 피그마 변경분이 있다면 반영되지 않을 수 있습니다.

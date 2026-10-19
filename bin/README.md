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
| `/notices` | notices.jsp | 공지 목록 및 상세 |

공통 헤더·사이드바는 `WEB-INF/views/fragments/top.jspf`, 하단은 `bottom.jspf`입니다. 선택 메뉴, 작은 4단계 결재 표시, 로딩·빈 결과·실패 표시와 반응형 배치를 포함합니다. 헤더 검색은 목록 화면 검색으로 연결됩니다. 사용자 정보는 고정된 시연 사용자입니다. 화면과 요청 날짜는 한국 시간 기준입니다.

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
- `GET /api/requests/{id}/results/{number}`, `GET /api/requests/{id}/results/{number}/csv`
- `GET /api/notices`

`DemoService`는 고정 사용자와 메모리 예시 데이터만 사용합니다. 실제 AI·조회 DB·SSO·조직/권한·전자결재·DBA 재실행 승인·감사 로그·실제 파일 저장/정리 기능은 연결되어 있지 않습니다. 화면에 표시하는 처리 상태는 이 시연 데이터의 상태입니다. 민감 요청을 임의로 승인하는 API는 제공하지 않습니다.

실서비스로 전환할 때는 인증 세션에서 요청자를 식별하고 모든 조회/결과 API에서 현재 권한을 재검증해야 합니다. 제출 SQL 불변성, 읽기 전용 DB 계정, 실행 제한, SQL별 이력, 결과 접근 이력과 30일 보관 정책을 백엔드에서 구현해야 합니다. 쿠키 기반 인증을 붙일 때 CSRF 방어도 함께 구성하세요.

## 내부망 배포

런타임에 외부 API·CDN을 요청하지 않습니다. Figma의 Noto Sans KR 폰트를 `static/fonts`에 포함했으며 `OFL.txt` 라이선스도 함께 보관합니다. CSS의 `@font-face`가 내부 서버에서 글꼴을 제공합니다. 빌드 의존성과 JDK는 승인된 반입 절차로 내부망에 준비합니다. 외부 AI가 아닌 내부 LLM 연결은 별도 백엔드 작업입니다.

디자인 원본: https://www.figma.com/design/rIEPy29qllAn2arsZR0GGr?node-id=11-56
피그마 호출 한도로 인해, 이번 코드 구현은 대화에서 생성한 Figma 구성 코드와 확인된 메인 캡처 및 프로토타입 정의를 기준으로 작성했습니다. 별도의 피그마 변경분이 있다면 반영되지 않을 수 있습니다.

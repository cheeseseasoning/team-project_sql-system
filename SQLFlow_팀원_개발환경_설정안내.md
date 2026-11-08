# SQLFlow 팀원 개발환경 설정 안내

Windows PC에서 SQLFlow를 실행하고, EC2의 Oracle·MySQL 및 CSV 결과 폴더를 함께 사용하는 방법입니다.

각 팀원은 자신의 PC에서 Spring Boot 앱을 실행합니다. DB는 SSH 터널로 연결하고, CSV 폴더는 SSHFS로 Windows 드라이브에 연결합니다. 팀원 PC에 Oracle·MySQL 서버나 Docker를 설치할 필요는 없습니다.

이 문서는 2026-11-08에 로컬 앱의 DB 연결과 EC2 CSV 미리보기를 확인한 구성을 기준으로 합니다.

## 1. 연결 구조와 공통 주소

```text
내 브라우저 → localhost:8080 → 내 PC의 SQLFlow
                                ├─ 127.0.0.1:11521 → SSH 터널 → EC2 Oracle:1521
                                ├─ 127.0.0.1:13306 → SSH 터널 → EC2 MySQL:3306
                                └─ Z:/ → SSHFS/SFTP → EC2 CSV 결과 폴더
```

| 구분 | 현재 설정 | 설명 |
|---|---|---|
| 로컬 웹 화면 | http://localhost:8080 | STS에서 실행한 앱 |
| SSH 서버 | `13.124.37.144:22` | 현재 공통 스크립트의 접속 대상 |
| SSH 사용자 | `ubuntu` | 현재 BAT의 접속 사용자 |
| Oracle 터널 입구 | `127.0.0.1:11521` | 내 PC에 열리는 포트 |
| Oracle 서비스 | `XEPDB1` | DB 계정: `SQLFLOW` |
| MySQL 터널 입구 | `127.0.0.1:13306` | 내 PC에 열리는 포트 |
| MySQL 데이터베이스 | `sqlflow_demo` | DB 계정: `sqlflow_reader` |
| EC2 CSV 폴더 | `/home/ubuntu/sqlflow-deploy/storage/query-results` | EC2 앱이 사용하는 실제 결과 폴더 |
| 로컬 CSV 폴더 | `Z:/` | 위 EC2 폴더를 연결한 드라이브의 예시 |

`127.0.0.1`과 `localhost`는 내 PC를 뜻합니다. `11521`·`13306`은 EC2의 공개 DB 포트가 아니라 **내 PC의 SSH 터널 입구**입니다. 앱 설정에 `13.124.37.144:11521` 또는 `13.124.37.144:13306`을 넣지 않습니다.

DB 연결 스크립트와 CSV 드라이브 연결은 별개입니다. `tools/connect-db.bat`은 DB 터널만 생성하고, `Z:` 드라이브를 자동으로 연결하지 않습니다. 스크립트와 설치 파일의 위치는 [EC2 DB·CSV 연결 가이드](<tools/EC2 DB·CSV 연결 가이드.md>)에서 확인할 수 있습니다.

EC2 IP·SSH 사용자·서비스명 등이 변경되면 환경 담당자가 변경 사항을 공유합니다. DB 비밀번호와 웹 로그인 계정은 별도로 전달받습니다. SSH 키, DB 접속 계정, 웹 로그인 계정은 서로 다른 인증 정보입니다.

## 2. 매번 개발을 시작할 때

최초 설정을 마쳤다면 다음 순서로 사용합니다.

1. 프로젝트의 `tools` 폴더에서 `connect-db.bat`을 더블클릭하고 연결 창을 열어 둡니다.
2. SSHFS-Win-Manager에서 CSV 연결을 시작합니다.
3. 탐색기에서 `Z:` 바로 아래에 EC2 CSV 파일이 보이는지 확인합니다.
4. STS에서 SQLFlow를 실행합니다.
5. http://localhost:8080 에서 로그인합니다.

개발 종료 시 STS 앱을 먼저 종료하고 CSV 드라이브 연결을 해제합니다. DB 터널은 해당 창에서 `Ctrl+C`로 종료합니다. 재시작·절전 등으로 연결이 끊겼다면 DB 터널과 CSV 연결을 다시 시작합니다.

## 3. 최초 준비: 프로그램 설치

| 프로그램 | 용도·기준 |
|---|---|
| JDK 21 | 프로젝트 빌드·실행 |
| Spring Tools for Eclipse(STS) 5.4.0 | 개발 도구 |
| Lombok | STS에서 Lombok 코드 인식 |
| Git for Windows | 소스 다운로드·업데이트 |
| Windows OpenSSH Client | `tools/connect-db.ps1`에서 실행하는 `ssh` 명령 |
| WinFsp | 원격 폴더를 Windows 드라이브로 연결하는 기반 |
| SSHFS-Win | SSH/SFTP 파일시스템 연결 |
| SSHFS-Win-Manager | 서버·키·폴더 연결을 관리하는 GUI |
| DBeaver | 선택 사항: DB 직접 확인 |

다운로드 및 설치 안내:

- [JDK 21](https://adoptium.net/temurin/releases/?version=21)
- [STS](https://spring.io/tools/)
- [Lombok 설치 안내](https://projectlombok.org/setup/eclipse)
- [Git for Windows](https://git-scm.com/install/windows)
- [WinFsp](https://winfsp.dev/rel/)
- [SSHFS-Win 설치 안내](https://github.com/winfsp/sshfs-win#installation)
- [SSHFS-Win-Manager](https://github.com/evsar3/sshfs-win-manager/releases)

파일 연결 도구는 **WinFsp → SSHFS-Win → SSHFS-Win-Manager** 순서로 설치합니다. Manager만 설치해서는 드라이브를 연결할 수 없습니다.

공유한 설치 파일은 `tools/installers` 폴더에 있습니다. 실제 파일명과 설치 순서는 [EC2 DB·CSV 연결 가이드](<tools/EC2 DB·CSV 연결 가이드.md>)를 참고하세요.

새 PowerShell 창에서 기본 명령을 확인합니다.

```powershell
java -version
javac -version
git --version
ssh -V
```

Java·javac는 21 계열을 사용합니다. `ssh` 명령을 찾을 수 없다면 Windows의 선택적 기능에서 OpenSSH 클라이언트를 설치합니다.

### STS·Lombok 설정

1. STS의 **Window → Preferences → Java → Installed JREs**에 JDK 21을 등록합니다.
2. **Execution Environments → JavaSE-21**에도 해당 JDK를 지정합니다.
3. **General → Workspace → Text file encoding**은 UTF-8로 설정합니다.
4. STS를 종료한 뒤 `lombok.jar`를 실행하고, 설치 대상에 현재 STS 실행 파일을 지정합니다.
5. STS를 다시 실행합니다. 다른 STS에 설치한 Lombok은 새 STS에 자동 적용되지 않습니다.

STS 자체 실행용 Java와 프로젝트 실행용 JDK는 다를 수 있습니다. 프로젝트·JUnit·Spring Boot 실행 설정에는 JDK 21을 지정합니다.

### 소스 가져오기

환경 담당자에게 저장소 URL과 작업 브랜치를 전달받아 clone합니다. 기존 작업 폴더가 있다면 변경 사항을 먼저 정리한 뒤 팀 브랜치를 업데이트합니다.

STS에서 **File → Import → Maven → Existing Maven Projects**로 `pom.xml`이 있는 폴더를 가져옵니다. 의존성 다운로드가 끝나면 **프로젝트 우클릭 → Maven → Update Project**를 실행합니다.

프로젝트의 라이브러리 버전은 `pom.xml`을 기준으로 유지합니다. 실행은 STS를 사용하며, 과거 README에 안내된 `run.ps1`, `setup.ps1`, `maven.ps1`은 현재 저장소에 없습니다.

## 4. 최초 설정: SSH 키와 DB 터널

### 개인 키 경로 등록

환경 담당자에게 사용 권한이 있는 SSH 개인 키와 서버 접속 정보를 전달받습니다. 키 파일은 프로젝트 폴더 밖에 보관합니다.

Windows에서 **환경 변수 편집 → 환경 변수 → 사용자 변수 → 새로 만들기**로 다음 변수를 등록합니다.

```text
변수 이름: SQLFLOW_SSH_KEY
변수 값:   C:\keys\본인의키파일.pem
```

값은 실제 키 파일 경로로 입력하고 따옴표를 붙이지 않습니다. 팀원마다 경로는 달라도 변수 이름은 같습니다. 기존 터미널에서 실행할 경우 새 창을 열어 환경변수를 반영합니다. 기존 프로그램에서 BAT를 실행한다면 그 프로그램도 다시 실행합니다.

### DB 연결 시작

프로젝트의 `tools` 폴더에 있는 [connect-db.bat](tools/connect-db.bat)을 더블클릭합니다. BAT와 `connect-db.ps1`은 같은 `tools` 폴더에 있어야 합니다.

프로젝트 루트의 PowerShell에서 실행할 때는 다음 명령을 사용합니다.

```powershell
.\tools\connect-db.bat
```

BAT는 `%~dp0`로 자신의 폴더에 있는 PS1을 찾으므로 두 파일을 함께 이동했다면 스크립트 내부 경로를 바꿀 필요가 없습니다. STS 앱의 Working directory는 스크립트 위치와 관계없이 **프로젝트 루트**로 유지합니다.

처음 접속할 때 서버 지문 확인이 표시될 수 있습니다. 환경 담당자가 전달한 지문과 일치하는지 확인한 뒤 연결합니다. 키에 암호가 설정되어 있다면 입력 안내에 따라 입력합니다.

```text
DB SSH 터널을 연결합니다.
Oracle: localhost:11521
MySQL : localhost:13306
연결된 동안 이 창을 열어 두세요. 종료: Ctrl+C
```

오류 없이 위 상태로 대기하면 정상적인 터널 실행 형태입니다. `-N` 옵션 때문에 서버의 셸 화면은 표시되지 않습니다. 다만 이 상태만으로 실제 DB 인증과 조회까지 확인된 것은 아닙니다.

별도 PowerShell에서 로컬 터널 포트를 확인할 수 있습니다.

```powershell
Test-NetConnection 127.0.0.1 -Port 11521
Test-NetConnection 127.0.0.1 -Port 13306
```

`TcpTestSucceeded: True`는 로컬 터널 입구에 연결할 수 있다는 뜻입니다. EC2 내부 DB까지의 연결과 DB 계정 인증은 로그인·DB 연결 테스트로 확인합니다.

현재 PS1은 EC2 기준 `127.0.0.1:1521`, `127.0.0.1:3306`으로 전달합니다. DB 호스트나 포트가 변경되면 담당자가 스크립트 설정을 확인해야 합니다. EC2의 DB 포트를 PC에 직접 공개하지 않아도 SSH 접속을 통해 개발할 수 있습니다.

### DBeaver와 함께 사용하기

EC2의 같은 DB에 DBeaver와 SQLFlow가 동시에 접속하는 것은 가능합니다. 단, 같은 PC에서 터널 두 개가 동일한 로컬 포트를 열 수는 없습니다.

- DBeaver의 기존 SSH 터널을 유지한다면 로컬 포트가 `11521`·`13306`과 겹치지 않는지 확인합니다.
- 공통 BAT의 터널을 공유하려면 DBeaver의 SSH 기능을 끄고 호스트를 `127.0.0.1`, 포트를 각각 `11521`·`13306`으로 지정합니다.

## 5. 최초 설정: EC2 CSV 폴더 연결

SSHFS-Win-Manager에서 새 연결을 만들고 다음 정보를 입력합니다. 실제 UI의 항목 이름은 설치 버전에 따라 다를 수 있습니다.

| 항목 | 값 |
|---|---|
| 연결 이름 | `SQLFlow CSV` |
| 서버 / 호스트 | `13.124.37.144` |
| SSH 포트 | `22` |
| 사용자 | `ubuntu` |
| 인증 방식 | 개인 키 / Private Key |
| 개인 키 | 본인이 사용하는 SSH 키 파일 |
| 원격 경로 | `/home/ubuntu/sqlflow-deploy/storage/query-results` |
| 드라이브 문자 | 사용하지 않는 문자, 이 문서에서는 `Z:` |

Manager에서는 환경변수가 자동 적용된다고 가정하지 말고, 키 파일 선택 기능으로 실제 개인 키를 지정합니다.

연결 후 탐색기에서 `Z:`를 엽니다. **`Z:`의 최상위가 EC2의 `query-results` 폴더**여야 합니다. 기존 CSV가 있다면 바로 보여야 합니다. 드라이브 문자를 다르게 선택했다면 아래 앱 설정도 같은 문자로 바꿉니다.

탐색기에서 파일이 보이는 것은 연결·열람 확인입니다. 로컬 앱이 CSV를 새로 저장하려면 SSH 사용자에게 해당 원격 폴더의 쓰기 권한이, 결과 정리에는 삭제 권한도 있어야 합니다. 로컬 앱과 EC2 앱은 같은 실제 파일을 사용합니다.

STS는 드라이브를 연결한 동일한 Windows 사용자로 일반 권한에서 실행합니다. 드라이브 연결 도구와 STS의 사용자·권한 수준이 다르면 탐색기에서는 보이지만 앱에서는 접근하지 못할 수 있습니다.

## 6. 최초 설정: 로컬 프로퍼티스

프로젝트 루트의 `application-local.properties.example`을 복사하고 복사본 이름을 `application-local.properties`로 변경합니다. `pom.xml`과 같은 폴더에 두고 `.txt` 확장자가 붙지 않았는지 확인합니다.

**현재 예제 파일에 이전 내부망 IP와 공유 폴더 값이 남아 있다면 그대로 사용하지 말고, 아래 SSH 터널용 값으로 교체합니다.** 이 문서는 로컬 PC 실행용이며, EC2 배포 앱의 설정에 그대로 적용하지 않습니다.

```properties
# 내 PC에서 실행 중인 SSH 터널을 통해 Oracle 접속
spring.datasource.url=jdbc:oracle:thin:@//127.0.0.1:11521/XEPDB1
spring.datasource.username=SQLFLOW
spring.datasource.password=별도로_전달받은_Oracle_비밀번호

# 내 PC에서 실행 중인 SSH 터널을 통해 MySQL 접속
app.datasource.query.url=jdbc:mysql://127.0.0.1:13306/sqlflow_demo?sslMode=REQUIRED
app.datasource.query.username=sqlflow_reader
app.datasource.query.password=별도로_전달받은_MySQL_비밀번호
app.datasource.query.driver-class-name=com.mysql.cj.jdbc.Driver

# EC2 query-results 폴더를 연결한 Windows 드라이브
sqlflow.result.storage-root=Z:/
```

- 두 비밀번호는 실제 전달받은 값으로 교체합니다. 임의의 따옴표를 붙이지 않습니다.
- `sslMode=REQUIRED`는 MySQL TLS 연결 옵션입니다. SSH 터널과 별개이므로 유지합니다.
- 로컬 앱에 `/home/ubuntu/...`를 넣어도 EC2 폴더로 자동 연결되지 않습니다. 로컬 앱에서는 연결한 Windows 드라이브를 사용합니다.
- EC2 앱은 기존 Linux 저장 경로를 유지하고, 로컬 앱만 `Z:/`로 설정합니다.
- 앱은 저장 루트와 DB의 `QUERY_RESULT.FILE_PATH` 상대 경로를 합쳐 CSV를 찾습니다. DB와 같은 저장 폴더를 사용해야 기존 결과도 읽을 수 있습니다.
- 설정을 변경한 뒤에는 STS 앱을 완전히 종료하고 다시 실행합니다.

공통 `application.properties`는 다음 설정으로 로컬 파일을 읽습니다.

```properties
spring.config.import=optional:file:./application-local.properties
```

따라서 **Spring Boot와 JUnit 실행 설정의 Working directory를 프로젝트 루트로 지정**해야 합니다. 서버 주소가 맞는데도 다른 DB로 연결된다면 실행 설정의 환경변수·프로그램 인자 등 별도 설정도 확인합니다.

### Git 공유 범위와 인코딩

| 항목 | 공유 기준 |
|---|---|
| `tools/connect-db.bat`, `tools/connect-db.ps1` | Git에 커밋 |
| `application-local.properties.example` | 실제 비밀번호 없이 Git에 커밋 |
| `application-local.properties` | Git 제외, 개인 PC에서 작성 |
| SSH 개인 키 | 프로젝트 밖에서 개인 보관, Git에 올리지 않음 |
| `SQLFLOW_SSH_KEY` | 각 PC의 사용자 환경변수로 등록 |
| SSHFS-Win-Manager 개인 연결 설정 | 각 PC에서 등록 |
| `.factorypath`의 개인 경로 변경 | 연결 도구 커밋에 포함하지 않음 |

현재 BAT는 `powershell.exe`로 Windows PowerShell을 실행합니다. 한글이 있는 PS1은 **UTF-8 BOM 포함**, 영어로 작성한 BAT는 **UTF-8 BOM 없이** 저장합니다. 이 인코딩 예외는 해당 스크립트에만 적용하며 프로젝트의 다른 파일을 일괄 변환하지 않습니다.

로컬 설정의 Git 제외 여부는 다음으로 확인합니다.

```powershell
git check-ignore application-local.properties
git ls-files -- application-local.properties
```

첫 명령은 경로가 출력되고 두 번째 명령은 출력이 없어야 합니다. 이미 추적 중인 파일은 `.gitignore` 추가만으로 추적이 해제되지 않습니다.

## 7. STS 실행과 연결 검증

### 앱 실행

1. DB 터널과 CSV 드라이브를 먼저 연결합니다.
2. STS에서 `SqlFlowApplication.java`를 **Run As → Spring Boot App**으로 실행하거나 Boot Dashboard를 사용합니다.
3. 해당 실행 설정의 JRE는 JDK 21, Working directory는 프로젝트 루트로 지정합니다.
4. http://localhost:8080 에서 발급받은 웹 계정으로 로그인합니다.
5. 요청 목록과 실행 결과를 확인합니다. 기존 성공 결과의 미리보기·CSV 다운로드를 확인합니다.
6. 새 SQL 실행 확인이 필요하면 팀에서 합의한 테스트 요청으로 결과 파일 생성까지 확인합니다.

로그인은 Oracle의 사용자 정보를 사용합니다. 화면이 열려도 Oracle 연결이 되지 않으면 로그인은 실패할 수 있습니다. 앱 시작 로그만으로 DB 접속 성공을 판단하지 않습니다.

### 선택: JUnit DB 연결 테스트

다음 파일을 우클릭하여 **Run As → JUnit Test**로 실행할 수 있습니다.

```text
src/test/java/com/sqlflow/config/DualDataSourceConnectionTest.java
```

| 테스트 | 확인 내용 |
|---|---|
| `managementConnectionUsesOracle` | 관리 연결의 실제 DB가 Oracle인지 |
| `queryConnectionUsesMySql` | 조회 연결의 실제 DB가 MySQL인지 |
| `mySqlMapperReadsUsingReaderAccount` | DB명·reader 계정과 `connection_test`의 준비된 데이터 |

세 번째 테스트는 `sqlflow_reader@%` 계정과 `connection_test`의 1번 데이터가 `MySQL connection success`임을 기대합니다. 서버의 테스트 데이터·계정 구성이 다르면 연결 자체가 성공해도 이 테스트는 실패할 수 있습니다. 테스트 데이터는 담당자와 확인하고 임의로 변경하지 않습니다.

JUnit 실행 설정도 JDK 21과 프로젝트 루트 Working directory를 사용합니다. DB 터널을 열어 둬야 합니다. 이 테스트는 CSV 드라이브나 전체 요청·결재·실행 흐름을 검증하지 않습니다.

## 8. 자주 발생하는 문제

| 증상 | 확인·조치 |
|---|---|
| `SQLFLOW_SSH_KEY` 안내가 나오고 BAT 종료 | 사용자 환경변수를 등록하고 새 실행 환경에서 다시 시작 |
| SSH 키 파일을 찾을 수 없음 | 환경변수 값의 실제 파일 위치·확장자 확인. 값에는 따옴표를 넣지 않음 |
| `ssh` 명령을 찾을 수 없음 | Windows OpenSSH 클라이언트 설치 여부 확인 |
| SSH `Permission denied (publickey)` | 서버 사용자명·키 파일·서버의 키 등록 상태 확인 |
| `bind ... Permission denied` / `Address already in use` | 내 PC의 터널 포트 사용·예약·보안 제한 확인. 포트 변경 시 PS1과 프로퍼티스를 함께 변경 |
| SSH `Could not request local forwarding` | 위쪽 오류에서 어떤 로컬 포트를 열지 못했는지 확인 |
| 터널 창에 접속 안내만 나오고 대기 | `-N` 옵션의 정상 실행 형태. 창을 열어 둔 채 앱 실행 |
| EC2 IP의 1521·3306 TCP 검사 실패 | SSH 터널 구성에서는 직접 접근이 안 될 수 있음. 로컬 11521·13306을 검사 |
| `ORA-12541`에서 EC2 IP와 11521 표시 | Oracle URL을 `127.0.0.1:11521`로 수정하고 앱 재시작 |
| SSH 창에 `connect failed: Connection refused` | EC2 기준 전달 대상 호스트·DB 포트·리스너 실행 상태를 담당자가 확인 |
| `ORA-01017` | Oracle DB 계정·비밀번호 확인. 웹 로그인 비밀번호와 구분 |
| `ORA-12514` | 서비스명 `XEPDB1`과 서버 PDB·리스너 상태 확인 |
| MySQL `Access denied` | DB 계정·비밀번호와 실제 접속 대상 확인 |
| MySQL `Public Key Retrieval is not allowed` | 기존 JDBC 옵션 `sslMode=REQUIRED` 적용 여부 확인 |
| `winfsp-x64.dll` 없음 | WinFsp 설치·복구 후 Manager를 다시 실행. DLL만 별도로 복사하지 않음 |
| 드라이브 연결은 됐지만 `Z:` 바로 아래 CSV가 없음 | 원격 경로가 정확한 `query-results` 폴더인지 확인 |
| 탐색기에서는 파일이 보이지만 앱에서 못 읽음 | STS의 사용자·실행 권한, 드라이브 문자, 설정 반영·앱 재시작 확인 |
| 저장된 CSV 파일을 찾을 수 없음 | 저장 루트와 DB `FILE_PATH`를 합친 위치에 실제 파일이 있는지 확인 |
| 파일이 있어도 미리보기·다운로드가 차단됨 | 실행 성공 여부, 결과 만료·삭제, 현재 접근 정책과 오류 응답 확인 |
| 기존 결과는 읽지만 새 CSV 저장 실패 | 원격 폴더 쓰기 권한과 드라이브 연결 상태 확인 |
| 설정을 수정해도 반영되지 않음 | 로컬 파일명·위치·Working directory·실행 설정의 별도 값 확인 후 앱 재시작 |
| 8080 포트 사용 중 | 기존 로컬 앱 종료 후 재실행 |
| Lombok 생성 메서드 오류 | 현재 STS에 Lombok 설치 후 완전히 종료·재실행 |
| Java·의존성 오류 | JDK 21 지정, Maven Update Project와 다운로드 오류 확인 |

CSV 오류를 확인할 때는 브라우저 F12 → Network에서 `/preview` 또는 `/csv` 요청의 상태 코드·응답 문구를 확인합니다. 로그인 오류는 STS Console의 `Caused by`, `ORA-...` 등 DB 오류를 함께 확인합니다. 실제 비밀번호나 개인 키 내용은 공유하지 않습니다.

## 9. 완료 확인과 이후 업데이트

- [ ] SSH 키 환경변수와 키 파일 경로 확인
- [ ] `tools/connect-db.bat` 실행 후 로컬 11521·13306 포트 연결 확인
- [ ] CSV 드라이브 연결 후 탐색기에서 EC2 파일 확인
- [ ] 로컬 프로퍼티스의 DB 주소·저장 경로·개인 인증 정보 설정
- [ ] STS/JUnit의 JDK 21·프로젝트 루트 Working directory 확인
- [ ] 로컬 웹 로그인과 요청 목록 조회 확인
- [ ] 권한이 있는 기존 성공 결과의 미리보기·CSV 다운로드 확인
- [ ] 필요한 경우 합의된 테스트 요청으로 새 CSV 저장 확인

공통 변경은 **pull → 필요 시 Maven Update Project → 앱 재시작**으로 반영합니다. Git에서 제외한 `application-local.properties`와 개인 환경변수·Manager 설정은 `git pull`로 갱신되지 않습니다. 서버 IP·포트·드라이브 문자가 바뀌면 담당자의 안내에 따라 함께 수정합니다.

환경 담당자는 다음을 전달합니다.

- 저장소 URL과 작업 브랜치
- EC2 SSH 서버 주소·사용자·서버 지문과 승인된 개인 키 사용 방법
- Oracle 서비스명·DB 계정과 별도 비밀번호
- MySQL DB명·reader 계정과 별도 비밀번호
- EC2 CSV 폴더 경로와 접근 권한
- 공통 스크립트·연결 정보 변경 사항

과거 Windows 공용 DB PC의 내부망 IP, WSL 포트 전달 안내는 현재 EC2·SSH 기반 개발 절차에 적용하지 않습니다. EC2 자체 배포·컨테이너·방화벽 관리는 서버 담당자가 별도로 관리합니다.

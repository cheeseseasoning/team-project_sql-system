# SQLFlow 팀원 개발환경 설정 안내

Windows 새 PC에서 프로젝트를 받아 실행하기 위한 안내입니다.

각자의 PC에서 Spring Boot 서버를 실행하고, 데이터베이스는 공용 Oracle에 접속합니다.

| 구분 | 주소 |
|---|---|
| 각자 실행한 웹 서버 | http://localhost:8080 |
| 공용 Oracle | 192.168.3.131:1521/XEPDB1 |
| DB 계정 | SQLFLOW |
| DB 비밀번호 | 환경 담당자가 별도 전달 |

`localhost`는 자신의 PC를 뜻합니다. 웹 접속에는 localhost를 사용하고, 팀원 PC의 DB 설정에는 공용 DB IP를 사용합니다.

## 1. 프로그램 준비

| 프로그램 | 팀 기준 | 비고 |
|---|---|---|
| JDK | 21 | 가능하면 팀에서 동일한 배포판·패치 버전 사용 |
| Spring Tools for Eclipse | 5.4.0 | 이하 STS |
| Lombok | 팀에서 공유한 설치본 | 각자의 STS에 설치 |
| Git | Git for Windows | 소스 다운로드·업데이트 |
| SQL Developer | 선택 | DB를 직접 조회하거나 SQL을 작성할 때 사용 |

Spring Boot 4.1.1, MyBatis Starter 4.0.0, Oracle JDBC, JUnit 등의 라이브러리는 `pom.xml`을 통해 내려받습니다. 팀원 PC에 Oracle 서버를 별도로 설치할 필요는 없습니다. 우선 STS 내장 Maven을 사용합니다.

다운로드:

- JDK 21: https://adoptium.net/temurin/releases/?version=21
- STS: https://spring.io/tools/
- Lombok 설치 안내: https://projectlombok.org/setup/eclipse
- Git: https://git-scm.com/install/windows

## 2. JDK 21 설치

1. Windows x64용 **JDK 21**을 설치합니다. JRE가 아닌 JDK를 선택합니다.
2. 설치된 JDK 폴더 경로를 확인합니다.
3. 터미널에서도 사용할 경우 `JAVA_HOME`을 JDK 폴더로 지정하고, Path에 `%JAVA_HOME%\bin`을 추가합니다.
4. 새 PowerShell 창에서 확인합니다.

```powershell
java -version
javac -version
```

두 결과 모두 21 계열인지 확인합니다. STS가 사용할 JDK는 다음 단계에서 별도로 지정합니다.

## 3. STS 설치 및 Java 설정

### STS 실행

1. 아래 Windows 설치본을 다운로드합니다.

   ```text
   spring-tools-for-eclipse-5.4.0.RELEASE-e4.41.0-win32.win32.x86_64.zip
   ```

2. 압축을 완전히 해제한 뒤 실행 파일을 실행합니다.
3. 새 workspace를 지정합니다. 예: `C:\workspace-sqlflow`

### 프로젝트용 JDK 지정

1. **Window → Preferences → Java → Installed JREs**
2. **Add → Standard VM → Directory**
3. 설치한 JDK 21 폴더 선택
4. 추가된 JDK 21을 기본값으로 체크
5. **Installed JREs → Execution Environments → JavaSE-21**에서도 JDK 21 선택
6. **Apply and Close**

STS에 포함된 Java 25는 IDE 실행용으로 유지합니다. 프로젝트 개발·실행에는 JDK 21을 사용합니다.

## 4. Lombok 설치

1. STS를 종료합니다.
2. 전달받거나 다운로드한 `lombok.jar`를 실행합니다. 더블클릭이 안 되면 해당 파일이 있는 폴더의 PowerShell에서 실행합니다.

   ```powershell
   java -jar lombok.jar
   ```

3. 설치 대상에 **STS 5 실행 파일**을 지정합니다. 자동 검색되지 않으면 **Specify location**을 사용합니다.
4. 설치 후 STS를 완전히 종료했다가 다시 실행합니다.
5. **Help → About**에서 Lombok 적용 여부를 확인합니다.

기존 STS 3에 설치한 Lombok은 새 STS에 자동 적용되지 않습니다.

## 5. Git 설치 및 소스 다운로드

Git for Windows 설치 후 새 PowerShell에서 확인합니다.

```powershell
git --version
```

본인 이름과 커밋용 이메일을 등록합니다.

```powershell
git config --global user.name "본인 이름"
git config --global user.email "본인 이메일"
```

환경 담당자에게 **팀 저장소 URL과 작업할 브랜치**를 전달받습니다. 비공개 저장소라면 접근 권한도 필요합니다.

최초 다운로드는 원하는 소스 보관 폴더에서 실행합니다. `<팀_저장소_URL>`은 실제 주소로 바꿉니다.

```powershell
git clone <팀_저장소_URL>
```

이미 clone한 PC에서는 프로젝트 폴더에서 팀이 정한 브랜치로 이동한 뒤 업데이트합니다. 개인 변경 사항이 있을 때는 먼저 커밋 등으로 정리합니다.

```powershell
git pull
```

## 6. STS에 프로젝트 가져오기

1. **File → Import…**
2. **Maven → Existing Maven Projects → Next**
3. **Root Directory**에 clone한 프로젝트 폴더 지정
4. 목록의 `pom.xml` 체크
5. **Finish**
6. 의존성 다운로드·빌드가 끝날 때까지 대기
7. **프로젝트 우클릭 → Maven → Update Project… → OK**

확인할 설정:

| 위치 | 확인 내용 |
|---|---|
| Properties → Java Build Path → Libraries | JRE System Library가 JavaSE-21 |
| Properties → Java Compiler | 컴파일 기준 21 |
| Window → Preferences → General → Workspace | Text file encoding: UTF-8 |

STS·JDK·소스의 설치 경로는 개인마다 달라도 됩니다. `pom.xml`의 버전과 의존성은 팀 공통 기준으로 유지합니다.

## 7. DB 접속 설정

프로젝트 루트의 `application-local.properties.example`을 복사하고, 복사본 이름을 `application-local.properties`로 변경합니다.

**파일은 `pom.xml`과 같은 위치에 둡니다.** Windows에서 확장자를 숨기고 있다면 파일 확장자 표시를 켜서 `.txt` 등이 붙지 않았는지 확인합니다.

```text
team-project_sql-system/
├─ pom.xml
├─ .gitignore
├─ application-local.properties.example
├─ application-local.properties
└─ src/
   └─ main/resources/application.properties
```

`application-local.properties` 내용:

```properties
spring.datasource.url=jdbc:oracle:thin:@//192.168.3.131:1521/XEPDB1
spring.datasource.username=SQLFLOW
spring.datasource.password=별도로_전달받은_실제비밀번호
```

- 비밀번호 부분은 실제 값으로 교체합니다. 따옴표를 임의로 붙이지 않습니다.
- 팀원 PC에서는 DB 주소에 `localhost`를 사용하지 않습니다.
- DB가 설치된 PC 본인만 `localhost`를 사용할 수 있습니다.
- DB PC의 IP가 바뀌면 환경 담당자가 새 주소를 공유합니다.

공통 `application.properties`에는 이미 다음 설정이 있습니다.

```properties
spring.config.import=optional:file:./application-local.properties
```

따라서 Run Configurations에 DB 비밀번호 환경변수를 추가할 필요가 없습니다.

### Git 공유 범위

| 파일 | 공유 방법 |
|---|---|
| `application.properties` | Git에 커밋 |
| `application-local.properties.example` | Git에 커밋, 실제 비밀번호는 비움 |
| `application-local.properties` | Git 제외, 개인 PC에서 작성 |

프로젝트 루트에서 확인할 수 있습니다.

```powershell
git check-ignore application-local.properties
git ls-files -- application-local.properties
```

첫 명령은 파일 경로가 출력되고, 두 번째 명령은 출력이 없어야 합니다. 이미 추적 중인 파일은 `.gitignore` 추가만으로 추적이 해제되지 않으므로 환경 담당자에게 알립니다.

## 8. 공용 DB 네트워크 연결 확인

팀원 PC의 PowerShell에서 실행합니다.

```powershell
Test-NetConnection 192.168.3.131 -Port 1521
```

성공 기준:

```text
TcpTestSucceeded : True
```

False라면 환경 담당자와 아래를 확인합니다.

- DB PC가 켜져 있는지
- Oracle DB와 Listener가 실행 중인지
- 서로 접근 가능한 네트워크인지
- DB PC 방화벽이 팀원 PC의 TCP 1521 접속을 허용하는지
- DB PC의 IP가 변경되지 않았는지

이 단계는 포트 연결만 확인합니다. DB 계정·비밀번호와 MyBatis 연결은 다음 테스트에서 확인합니다.

## 9. JUnit DB 연결 테스트

프로젝트에 포함된 파일을 사용합니다.

```text
src/test/java/com/sqlflow/DbConnectionTest.java
```

1. STS에서 해당 파일 우클릭
2. **Run As → JUnit Test**
3. JUnit 창에서 **테스트 1개가 초록색으로 통과**하는지 확인

테스트는 MyBatis Mapper로 아래 SQL을 실행하고 결과를 검사합니다.

```sql
SELECT 1 FROM DUAL;
SELECT USER FROM DUAL;
```

기대 결과는 각각 `1`, `SQLFLOW`입니다. 데이터 변경은 하지 않습니다.

테스트가 통과하면 **JUnit 실행 환경에서 Spring Boot → MyBatis → 공용 Oracle 연결**이 확인됩니다.

### 설정 파일을 못 찾거나 Java 버전 오류가 날 때

1. **Run → Run Configurations… → JUnit → DbConnectionTest**
2. **Arguments → Working directory**를 `pom.xml`이 있는 프로젝트 루트로 지정
3. **JRE** 탭에서 JDK 21 사용 확인
4. **Apply → Run**

JUnit 실행 설정과 Boot Dashboard의 서버 실행 설정은 별개입니다.

## 10. 서버 실행 및 브라우저 확인

Boot Dashboard에서 프로젝트를 실행합니다. 처음 실행할 때는 아래 방법도 가능합니다.

```text
SqlFlowApplication.java 우클릭
→ Run As
→ Spring Boot App
```

해당 Spring Boot 실행 설정도 확인합니다.

- **JRE:** JDK 21
- **Arguments → Working directory:** 프로젝트 루트

서버 시작 후 본인 PC의 브라우저에서 접속합니다.

http://localhost:8080

각자의 PC에서 서버를 실행했으므로 이 주소가 맞습니다. 공용 DB 주소 `192.168.3.131`과 혼동하지 않습니다.

참고로 DB 담당자 PC에서 실행한 웹 서버에 접속하려면 `http://192.168.3.131:8080`을 사용합니다. 이 경우 해당 PC에서 서버가 실행 중이고 웹 포트 접근도 허용되어 있어야 합니다. 현재 기본 개발 방식은 각자 서버 실행입니다.

## 11. 자주 발생하는 문제

| 증상 | 확인 방법 |
|---|---|
| Downloading external resources is disabled | Preferences → XML (Wild Web Developer) → Download external resources like referenced DTD, XSD 체크 후 파일 다시 열기 |
| Java 버전 관련 오류 | 프로젝트·JUnit·서버 실행 설정의 JDK가 모두 21인지 확인 |
| MyBatis·JDBC 클래스를 찾지 못함 | Maven Update Project, 의존성 다운로드 오류 확인 |
| ORA-01017 | 계정·비밀번호 확인 |
| ORA-12514 | 서비스 이름 XEPDB1 및 서버의 PDB·Listener 상태 확인 |
| DB 접속 시간 초과 | 공용 DB IP·포트, 네트워크·방화벽 확인 |
| DB 설정을 읽지 못함 | 파일 이름·프로젝트 루트 위치·Working directory 확인 |
| 8080 포트 사용 중 | 기존 서버를 종료한 뒤 다시 실행 |
| Lombok 생성 메서드에 오류 표시 | 새 STS에 Lombok 설치 여부 확인 후 완전히 종료·재실행 |

오류를 공유할 때는 실행한 작업과 Console의 `Caused by` 내용을 함께 전달합니다. 실제 비밀번호는 포함하지 않습니다.

## 12. 완료 확인 및 이후 업데이트

아래 두 가지가 성공하면 팀 공통 개발환경의 기본 검증이 완료됩니다.

- [ ] `DbConnectionTest` 초록색 통과
- [ ] 본인 PC에서 `http://localhost:8080` 화면 표시

기존 화면은 시연용 메모리 데이터를 사용합니다. 화면 표시와 실제 DB 연결 여부는 위 두 단계로 나눠 확인합니다.

이후 공통 설정·코드 변경은 **pull → 필요 시 Maven Update Project → 서버 재시작**으로 반영합니다. DB 주소·비밀번호가 변경된 경우에는 `application-local.properties`를 별도로 갱신합니다.

### 환경 담당자가 팀원에게 전달할 내용

- 팀 Git 저장소 URL 및 작업 브랜치
- 공용 DB 주소·서비스 이름·계정 및 별도 비밀번호
- 팀에서 사용할 JDK·STS·Lombok 설치본 또는 정확한 버전
- 공용 DB 사용 가능 시간 및 주소 변경 사항

이 안내는 현재 프로젝트 설정 기준입니다. 새 PC·다른 네트워크의 접속 성공 여부는 각 팀원의 JUnit 결과로 확인합니다.

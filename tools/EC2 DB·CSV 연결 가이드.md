# EC2 DB·CSV 연결 가이드

EC2의 DB에 연결하는 SSH 터널 스크립트와 CSV 결과 폴더를 Windows 드라이브로 연결하기 위한 설치 파일을 모아 둔 폴더입니다. 각 PC에서 처음 한 번 필요한 프로그램을 설치하고 연결 정보를 등록합니다. 프로젝트를 내려받는 것만으로 자동 설치되지는 않습니다.

**DB는 이 폴더의 [connect-db.bat](connect-db.bat), CSV는 SSHFS-Win-Manager로 각각 연결**합니다. BAT는 CSV 드라이브를 자동 연결하지 않습니다.

```text
tools/
├─ EC2 DB·CSV 연결 가이드.md
├─ connect-db.bat
├─ connect-db.ps1
└─ installers/
   ├─ winfsp-2.1.25156.msi
   ├─ sshfs-win-3.5.20357-x64.msi
   └─ sshfs-win-manager-setup-v1.3.1.exe
```

DB BAT 실행 전에는 사용자 환경변수 `SQLFLOW_SSH_KEY`에 프로젝트 밖에 보관한 개인 키의 전체 경로를 등록합니다. 값에 따옴표는 넣지 않습니다. 등록 절차와 DB 프로퍼티스는 [팀원 개발환경 설정 안내](../SQLFlow_팀원_개발환경_설정안내.md)를 참고하세요.

## 1. 설치 순서

Windows x64 PC에서 아래 순서대로 설치합니다. 이미 해당 프로그램이 설치되어 있다면 중복 설치할 필요는 없습니다.

| 순서 | 설치 파일 | 역할 |
|---|---|---|
| 1 | [winfsp-2.1.25156.msi](installers/winfsp-2.1.25156.msi) | 원격 파일을 Windows 드라이브로 사용할 수 있게 하는 기반 |
| 2 | [sshfs-win-3.5.20357-x64.msi](installers/sshfs-win-3.5.20357-x64.msi) | SSH/SFTP로 EC2 폴더 연결 |
| 3 | [sshfs-win-manager-setup-v1.3.1.exe](installers/sshfs-win-manager-setup-v1.3.1.exe) | 연결 정보를 입력하고 드라이브를 연결하는 GUI |

각 설치 파일을 더블클릭하고 설치 안내를 따릅니다. 관리자 권한 승인이 표시되면 설치 권한이 있는 계정으로 진행합니다. 설치 프로그램이 재부팅을 요청하면 재부팅 후 다음 단계를 진행합니다.

공식 프로젝트 안내:

- [WinFsp](https://winfsp.dev/)
- [SSHFS-Win](https://github.com/winfsp/sshfs-win)
- [SSHFS-Win-Manager](https://github.com/evsar3/sshfs-win-manager)

## 2. CSV 폴더 연결 등록

SSHFS-Win-Manager를 실행하고 새 연결을 추가합니다. 항목 이름은 UI에 따라 다를 수 있습니다.

| 항목 | 현재 팀 설정 |
|---|---|
| 연결 이름 | `SQLFlow CSV` |
| 서버 / 호스트 | `13.124.37.144` |
| SSH 포트 | `22` |
| 사용자 | `ubuntu` |
| 인증 방식 | 개인 키 / Private Key |
| 개인 키 파일 | 환경 담당자에게 전달받은 본인의 SSH 개인 키 |
| 원격 경로 | `/home/ubuntu/sqlflow-deploy/storage/query-results` |
| 드라이브 문자 | `Z:` — 이미 사용 중이면 다른 빈 문자 선택 |

키 파일은 프로젝트 밖에 보관하고 Manager에서 실제 파일을 선택합니다. DB BAT에서 사용하는 `SQLFLOW_SSH_KEY` 환경변수가 Manager에 자동 적용되는 것은 아닙니다. 개인 키나 비밀번호를 이 폴더에 넣지 않습니다.

처음 접속할 때 서버 지문 확인이 나오면 환경 담당자에게 전달받은 지문과 비교한 후 연결합니다. 서버 주소나 사용자명이 변경되면 담당자의 최신 안내를 따릅니다.

저장 후 연결 버튼을 누르고 탐색기에서 `Z:`를 엽니다. **드라이브 최상위가 EC2의 `query-results` 폴더**여야 하며, 기존 CSV가 있다면 바로 보여야 합니다.

## 3. 로컬 앱의 저장 경로 설정

프로젝트 루트의 개인 설정 파일 `application-local.properties`에 연결한 드라이브를 지정합니다.

```properties
sqlflow.result.storage-root=Z:/
```

다른 드라이브 문자를 선택했다면 그 문자로 바꿉니다. 이 값은 **로컬 PC에서 실행하는 앱용**이며 EC2 배포 앱의 Linux 경로 설정은 유지합니다.

```text
내 PC의 SQLFlow → Z:/ → SSHFS/SFTP → EC2의 query-results 폴더
```

기존 실행 결과를 읽으려면 앱이 연결한 관리 DB와 CSV 폴더가 같은 실행 결과를 가리켜야 합니다. 앱은 저장 루트와 DB의 `QUERY_RESULT.FILE_PATH` 상대 경로를 합쳐 파일을 찾습니다.

탐색기에서 CSV가 보이는 것은 열람 확인입니다. 새 CSV 저장과 파일 정리를 위해서는 SSH 사용자에게 원격 폴더의 쓰기·삭제 권한도 있어야 합니다. 로컬 앱과 EC2 앱은 같은 실제 결과 파일을 사용합니다.

## 4. 매번 개발할 때

1. 이 폴더의 [connect-db.bat](connect-db.bat)을 더블클릭하고 창을 열어 둡니다.
2. SSHFS-Win-Manager에서 `SQLFlow CSV`를 연결합니다.
3. 탐색기에서 연결한 드라이브의 CSV를 확인합니다.
4. STS에서 SQLFlow를 실행하고 `http://localhost:8080`으로 접속합니다.
5. 권한이 있는 성공 결과의 미리보기·CSV 다운로드를 확인합니다.

STS는 드라이브를 연결한 동일한 Windows 사용자로 일반 권한에서 실행합니다. 저장 경로를 변경했다면 앱을 완전히 종료하고 다시 실행합니다.

프로젝트 루트의 PowerShell에서 시작하려면 다음 명령을 사용합니다.

```powershell
.\tools\connect-db.bat
```

BAT는 `%~dp0`로 **BAT 자신이 있는 폴더**의 `connect-db.ps1`을 찾습니다. 두 파일을 같은 폴더에 두면 현재 터미널 위치와 관계없이 동작합니다. PS1은 환경변수로 전달받은 개인 키 경로를 사용하므로 `tools`로 이동한 것 때문에 코드를 변경할 필요는 없습니다.

STS 앱의 Working directory는 계속 **프로젝트 루트**로 유지합니다. `tools`로 변경하면 프로젝트 루트의 `application-local.properties`를 읽지 못할 수 있습니다.

종료할 때는 STS 앱을 먼저 종료하고 Manager에서 드라이브 연결을 해제합니다. DB 터널은 해당 창에서 `Ctrl+C`로 종료합니다. PC를 재시작하거나 연결이 끊긴 뒤에는 DB 터널과 CSV 드라이브를 다시 연결합니다.

## 5. 문제 해결

| 증상 | 확인할 내용 |
|---|---|
| `winfsp-x64.dll`을 찾을 수 없음 | WinFsp 설치 또는 복구 후 Manager를 완전히 종료했다가 다시 실행합니다. DLL만 따로 복사하지 않습니다. |
| 인증 실패 / `Permission denied (publickey)` | SSH 사용자명과 개인 키, EC2의 키 등록 상태를 확인합니다. |
| `Z:`를 사용할 수 없음 | 기존 드라이브와 겹치는지 확인하고 빈 문자를 선택합니다. 앱 설정에도 같은 문자를 지정합니다. |
| 드라이브는 보이지만 CSV가 없음 | 원격 경로가 정확한 `query-results` 폴더인지 확인합니다. |
| 탐색기에서는 보이지만 앱이 못 읽음 | STS의 사용자·실행 권한, 로컬 설정의 드라이브 문자, 앱 재시작 여부를 확인합니다. |
| 기존 파일은 읽지만 새 CSV 저장 실패 | 원격 폴더 쓰기 권한과 연결 상태를 확인합니다. |
| 파일이 있어도 미리보기가 차단됨 | 파일 경로 외에 결과 만료·삭제·접근 권한을 확인합니다. 브라우저 Network의 `/preview` 응답 문구를 확인합니다. |
| DB 연결이 안 됨 | `tools/connect-db.bat` 실행과 DB URL의 `127.0.0.1:11521`, `127.0.0.1:13306` 설정을 확인합니다. CSV 드라이브 연결과 DB 터널은 별개입니다. |

전체 개발환경 설정과 SSH 키 환경변수 등록은 [팀원 개발환경 설정 안내](../SQLFlow_팀원_개발환경_설정안내.md)를 참고하세요.

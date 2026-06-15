# VendingMachine

> TCP 소켓 기반 분산 자판기 시스템  
> 로드밸런서, 2개의 운영 서버, 1개의 백업 서버, 4개의 Swing GUI 클라이언트로 구성된 네트워크 프로그래밍 프로젝트입니다.

## Overview

이 프로젝트는 여러 자판기 클라이언트가 하나의 로드밸런서에 접속하고, 로드밸런서가 정상 동작 중인 백엔드 서버로 요청을 분산하는 구조를 구현합니다. 서버 간에는 자판기 상태를 동기화하며, 운영 서버 장애 시 백업 서버로 요청을 우회할 수 있습니다.

```text
Client1  Client2  Client3  Client4
   \       |        |       /
        LoadBalancer :9000
          /       |       \
   Server1     Server2   BackupServer
    :9001       :9002       :9003
```

## Features

| 구분 | 기능 |
| --- | --- |
| 클라이언트 | Java Swing 기반 자판기 GUI, 음료 구매, 화폐 투입, 반환 |
| 관리자 | 재고 보충, 음료명 변경, 가격 변경, 수금, 매출 조회, 비밀번호 변경 |
| 서버 | 클라이언트 요청 처리, 자판기별 상태 관리, 매출/재고/화폐 정보 관리 |
| 동기화 | 서버 간 `SYNC_STATE` 메시지로 상태 공유 |
| 로드밸런싱 | 라운드 로빈 방식으로 Server1/Server2에 연결 |
| 장애 대응 | `PING`/`PONG` 헬스체크 후 운영 서버 장애 시 BackupServer 사용 |

## Tech Stack

| 항목 | 내용 |
| --- | --- |
| Language | Java |
| GUI | Java Swing |
| Network | TCP Socket |
| Encoding | UTF-8, Base64 메시지 인코딩 |
| IDE | IntelliJ IDEA 권장 |

## Project Structure

```text
VendingMachine/
├─ VendingMachineGUI/
│  └─ src/
│     ├─ Client1.java
│     ├─ Client2.java
│     ├─ Client3.java
│     └─ Client4.java
├─ VendingMachineServer/
│  └─ src/
│     ├─ Server1.java
│     ├─ Server2.java
│     └─ BackupServer.java
├─ VendingLoadBalancer/
│  └─ src/
│     └─ LoadBalancer.java
├─ build/
├─ out/
├─ .gitignore
└─ README.md
```

## Port Map

| Component | Port | Role |
| --- | ---: | --- |
| LoadBalancer | `9000` | GUI 클라이언트 접속 진입점 |
| Server1 | `9001` | 운영 서버 1 |
| Server2 | `9002` | 운영 서버 2 |
| BackupServer | `9003` | 장애 대응용 백업 서버 |

기본 호스트는 모두 `127.0.0.1`로 설정되어 있습니다. 다른 PC, 라즈베리파이, Ubuntu 서버에서 분산 실행하려면 각 Java 파일의 host 값을 실제 서버 IP로 변경해야 합니다.

## How to Run

### 1. Compile

PowerShell 기준:

```powershell
New-Item -ItemType Directory -Force build\classes
javac -encoding UTF-8 -d build\classes VendingMachineServer\src\*.java VendingLoadBalancer\src\*.java VendingMachineGUI\src\*.java
```

### 2. Start Servers

서버는 각각 별도 터미널에서 실행하는 것을 권장합니다.

```powershell
java -cp build\classes Server1
java -cp build\classes Server2
java -cp build\classes BackupServer
```

### 3. Start Load Balancer

```powershell
java -cp build\classes LoadBalancer
```

### 4. Start Clients

필요한 클라이언트를 각각 실행합니다.

```powershell
java -cp build\classes Client1
java -cp build\classes Client2
java -cp build\classes Client3
java -cp build\classes Client4
```

## Usage

1. `Client1`~`Client4` 중 하나를 실행합니다.
2. `10`, `50`, `100`, `500`, `1000`원 단위로 화폐를 투입합니다.
3. 구매 가능한 음료 버튼을 선택합니다.
4. 잔돈 또는 반환 금액은 GUI에 표시됩니다.
5. 관리자 메뉴에서 재고, 가격, 매출, 보유 화폐를 관리합니다.

기본 관리자 비밀번호는 `12345678`입니다.

## Core Rules

| 항목 | 값 |
| --- | ---: |
| 음료 종류 | 8개 |
| 기본 재고 | 음료별 10개 |
| 최대 투입 금액 | 7,000원 |
| 1,000원권 최대 투입 | 5,000원 |
| 사용 가능 화폐 | 10원, 50원, 100원, 500원, 1,000원 |

## Protocol Summary

| Message | Description |
| --- | --- |
| `PING` / `PONG` | 서버 상태 확인 |
| `HELLO|{client}` | 클라이언트 접속 등록 |
| `STATUS` | 현재 자판기 상태 요청 |
| `INSERT|{money}` | 화폐 투입 |
| `BUY|{drinkId}` | 음료 구매 |
| `RETURN` | 투입 금액 반환 |
| `ADMIN_LOGIN|{password}` | 관리자 로그인 |
| `SYNC_STATE|{state}` | 서버 간 상태 동기화 |
| `PUSH_STATE|{state}` | 접속 클라이언트에 실시간 상태 반영 |

## Development Notes

- `.gitignore`는 IntelliJ IDEA 설정 파일과 `.iml` 파일을 제외하도록 구성되어 있습니다.
- `build/`, `out/` 같은 컴파일 산출물은 로컬 실행용으로만 사용하는 것이 좋습니다.
- 서버와 클라이언트의 한글 표시가 깨지면 Java 컴파일 옵션에 `-encoding UTF-8`을 지정하세요.
- 네트워크 분산 실행 시 방화벽에서 `9000`~`9003` 포트 허용이 필요합니다.

## License

Academic project for network programming coursework.

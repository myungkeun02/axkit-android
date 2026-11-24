# ax-kit 안드로이드 앱

집 맥에서 도는 ax-kit 비서와 이야기하는 안드로이드 앱이에요. **아이폰 앱(`ax-kit-ios`)과 화면·흐름·말이 똑같아요.**
Kotlin, Jetpack Compose, Material 3(아이폰 모양에 맞춘 색·둥근 카드·점토 캐릭터), Android 10(API 29) 이상.

서버는 [ax-kit](https://github.com/myungkeun02/axkit) 레포에 있어요(앱 프로토콜: `docs/APP-PROTOCOL.md` §8–§15).
아이폰과 똑같은지 화면마다 확인한 표: [PARITY.md](PARITY.md).

| 탭 | 하는 일 |
|---|---|
| 홈 | 비서와 대화. 입력칸이 비어 있으면 오른쪽 마이크로 말로 입력. 오른쪽으로 밀면(또는 왼쪽 위 버튼) 아래 깔린 봇 목록이 나와요. 봇·팀 방, 팀장회의, "개발 봇에게 맡겼어요 ›" 줄. |
| 결재 | 비서·봇이 물어본 것. 객관식 + 늘 보이는 직접 입력칸, "앞으로는 알아서" 체크. |
| 문서 | 3D 문서 우주(점과 선, 끌어서 돌리고 집어서 확대, 눌러서 열기)와 층별 목록. |
| 캘린더 | 일정(파랑)과 비서·봇이 한 일(초록). ＋로 일정 추가. |
| 더보기 | 설정 및 연결, 녹음해서 회의록 만들기, 알아서 하는 일, 루틴, 반복 작업, 지출 관리, 봇·AI 사용량, AI 연결하기, AI 등급과 용도, 맥 상태, 새 기능. |

## 구조

```
app/src/main/java/dev/axkit/
  core/          프로토콜 핵심(안드로이드에 기대지 않는 순수 Kotlin, 단위 테스트 대상)
                 Events · Views · ApiClient · EventStream(SSE, Last-Event-ID로 이어 받기) · Timeline · Outbox
                 Pairing(QR 링크·주소 규칙) · Recordings(4MB 조각, 끊기면 이어서) · Format · GraphLayout
  app/           AppModel(상태·연결), KeystoreCredentialStore, Recorder, RecordingService, Notifier, ShareActivity
  app/ui/design  색·글꼴·카드·버튼·점토 캐릭터·결재 카드·마크다운 (아이폰 Design/와 같은 모양)
  app/ui/screens 탭 화면
dev/mock-server.mjs   가짜 맥(아이폰 앱과 같은 파일, Node만 있으면 됨)
```

- 토큰(기기 토큰, Cloudflare Access 서비스 토큰)은 **Android Keystore의 AES-256-GCM 키로 봉인**해서만 저장해요. 키는 폰 밖으로 못 나가고, 앱 백업도 꺼 두었어요.
- 오프라인에 쓴 메시지는 `Outbox`가 파일에 두었다가 연결되면 순서대로 보내요(`clientId`로 중복 없이). 인증번호처럼 보이는 글은 10분 지나면 버려요.
- 스트림(`GET stream`)은 앱이 앞에 있을 때만 열고, 끊기면 2초부터 60초까지 늘려 가며 `Last-Event-ID`로 다시 붙고, 돌아오면 `events?after=`로 빈 곳을 채워요.
- 녹음은 AAC 모노 16kHz, 4MB 조각으로 올리고 끊기면 이어서 보내요. 화면이 꺼져도 녹음은 계속돼요(알림에 "녹음 중").
- 주소 규칙: `https` 또는 사설 주소의 `http`(Tailscale `100.64/10`, 집 와이파이, localhost)만 받아요.

## 빌드와 실행

필요: JDK 21, Android SDK(`local.properties`의 `sdk.dir`), Node 20+.

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

에뮬레이터에서 가짜 맥과 함께(에뮬레이터 안의 127.0.0.1을 맥으로 이어 줘요):

```bash
node dev/mock-server.mjs
```

```bash
adb reverse tcp:47790 tcp:47790 && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

앱 첫 화면의 **개발용 가짜 맥에 연결**(디버그 빌드에만 있음)을 누르면 끝이에요. 또는 링크로:

```bash
adb shell am start -a android.intent.action.VIEW -d "'axkit://pair?u=http%3A%2F%2F127.0.0.1%3A47790&n=dev&c=123456'"
```

AX(ax-hq) 맥처럼 보려면 ax-hq 레포의 가짜 서버를 쓰세요(같은 연결 코드 123456): `node <ax-hq>/ios/dev/ax-hq-mock.mjs --port 47791`.

가짜 맥에서 일을 만들어 보기: `curl -s http://127.0.0.1:47790/dev/card`(새 결재), `curl -s 'http://127.0.0.1:47790/dev/notice?text=hi'`.

## 터미널 (맥에 바로 SSH)

더보기 › 맥 › 터미널. 휴대폰이 처음 한 번 만든 Ed25519 열쇠(비밀 부분은 Android Keystore로 잠가 이 휴대폰에만)의 공개키 한 줄을
맥의 `~/.ssh/authorized_keys`에 넣으면 비밀번호 없이 들어가요. 휴대폰과 맥 모두 Tailscale이 켜져 있고, 맥은 설정 › 일반 › 공유 › 원격 로그인이 켜져 있어야 해요.
처음 붙은 맥의 열쇠는 기억해 두고, 나중에 바뀌면 묻습니다. 미리 넣어 둘 맥 목록은 `local.properties`(또는 gradle 속성)의 `AX_SSH_HOSTS`로 정해요(아이폰과 같은 형식).
라이브러리: sshj 0.41.2, BouncyCastle 1.84, ConnectBot termlib 0.3.14.

## 진짜 맥과 연결하기

1. 맥: `ax-kit phone pair --app --platform android` → QR과 `axkit://pair?...` 링크(10분, 한 번만).
2. 폰: **QR 찍기**(카메라) 또는 **연결 링크 붙여넣기**.
3. 집 밖에서는 폰에서 Tailscale을 켜 두세요(맥의 `100.x` 주소, tailnet 안에서만).
4. 연결 끊기: 더보기 › 설정 및 연결 › 연결 끊기(맥에서도 바로 폐기).

## 아이폰과 다른 점(안드로이드 방식에 맞춘 것)

- 뒤로 가기: 시스템 뒤로 제스처가 화면을 닫고, 봇 목록을 닫고, 방에서 나와요.
- 공유: 다른 앱의 공유 시트에서 "ax-kit" → 글·링크는 비서에게(맥에 닿지 않으면 나중에), 사진·파일은 `share`로.
- 알림: 안드로이드 13 이상은 연결 뒤 한 번 알림 허락을 물어요. 결재 알림에는 승인/거절 버튼이 있어요.
  ax-kit 서버는 지금 애플 푸시(APNs)만 보내므로, 안드로이드 알림은 **앱이 맥과 이어져 있는 동안 온 결재**만 떠요(FCM은 서버에 아직 없음).
- 홈 화면 위젯 "오늘과 결재": 기다리는 결재 수와 첫 질문, 오늘 일정. 앱이 쓴 내용만 읽어요.

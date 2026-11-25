# 아이폰 ↔ 안드로이드 화면 맞추기 (PARITY)

아이폰 앱(`ax-kit-ios`)의 화면마다 안드로이드 쌍둥이 화면과 상태를 적었어요. 왼쪽은 아이폰(`docs/screenshots/ios/`, ax-kit-ios에서 복사),
오른쪽은 이 앱을 에뮬레이터(Pixel 8, Android 16)에서 가짜 맥(`dev/mock-server.mjs`)에 붙여 찍은 것이에요.

상태: **완료** = 화면·말·동작이 아이폰과 같고 에뮬레이터에서 확인함. **실기기 확인 필요** = 만들었지만 에뮬레이터로는 확인할 수 없음.

## 한눈에

| 영역 | 아이폰 화면 | 아이폰 파일 | 안드로이드 파일 | 상태 |
|---|---|---|---|---|
| 연결 | 처음 연결 (PairView) | `PairView.swift` | `PairView.kt` | 완료 |
| 연결 | QR 찍기 (QRScannerSheet) | `PairView.swift` | `PairQRScanner.kt` | 완료 |
| 홈 | 대화 (HomeView + ChatRoomView) | `HomeView.swift, ChatRoomView.swift` | `HomeView.kt, ChatRoomView.kt` | 완료 |
| 홈 | 봇 목록 시트 (BotDrawer) | `HomeView.swift` | `HomeView.kt` | 완료 |
| 홈 | 오늘 (TodaySheet) | `RootView.swift` | `TodaySheet.kt` | 완료 |
| 홈 | 말로 입력 (Dictation) | `Dictation.swift` | `Dictation.kt` | 완료 · 실기기 확인 필요 |
| 홈 | 팀 방 · 봇끼리 대화 (RoomHeader) | `ChatRoomView.swift, TeamScreens.swift` | `ChatRoomView.kt, TeamScreens.kt` | 완료 |
| 홈 | 팀 추가 (AddTeamSheet) | `TeamScreens.swift` | `TeamScreens.kt` | 완료 |
| 홈 | 팀 설정 (TeamSheet) | `TeamScreens.swift` | `TeamScreens.kt` | 완료 |
| 홈 | 팀장회의 방 | `ChatRoomView.swift, TeamScreens.swift` | `ChatRoomView.kt` | 완료 |
| 결재 | 결재 (ApprovalsView + CardPanel) | `ApprovalsView.swift, CardPanel.swift` | `ApprovalsView.kt, design/CardPanel.kt` | 완료 |
| 결재 | 사진 있는 결재 (AttachedImages) | `Attachments.swift` | `design/Attachments.kt` | 완료 |
| 결재 | 자동 승인 시트 (AutonomyScreen) | `ApprovalsView.swift, AutonomyScreens.swift` | `AutonomyScreens.kt` | 완료 |
| 문서 | 문서 우주 3D (UniverseView) | `DocsView.swift` | `DocsUniverse.kt` | 완료 |
| 문서 | 목록 (예전 층별 목록·묶음을 합침) | `DocsView.swift` | `DocsView.kt` | 완료 |
| 문서 | 문서 (DocumentScreen) | `DocsView.swift` | `DocsView.kt` | 완료 |
| 캘린더 | 캘린더 (CalendarScreen) | `CalendarScreen.swift` | `CalendarScreen.kt` | 완료 |
| 캘린더 | 일정 추가 (NewEventSheet) | `CalendarScreen.swift` | `CalendarAddSheet.kt` | 완료 |
| 더보기 | 더보기 (MoreView) | `MoreView.swift` | `MoreView.kt` | 완료 |
| 더보기 | 반복 작업 (JobsScreen) | `MoreView.swift` | `MoreJobs.kt` | 완료 |
| 더보기 | 지출 관리 (SpendingScreen) | `SpendingScreen.swift` | `SpendingScreen.kt` | 완료 |
| 더보기 | 봇·AI 사용량 (UsageScreen) | `MoreView.swift` | `MoreUsage.kt` | 완료 |
| 더보기 | AI 연결하기 (ProvidersScreen) | `MoreView.swift` | `MoreProviders.kt` | 완료 |
| 더보기 | 맥 상태 (HardwareScreen) | `MoreView.swift` | `MoreHardware.kt` | 완료 |
| 더보기 | 알아서 하는 일 (AutonomyScreen) | `AutonomyScreens.swift` | `AutonomyScreens.kt` | 완료 |
| 더보기 | AI 등급과 용도 (ModelTiersScreen) | `AutonomyScreens.swift` | `AutonomyScreens.kt` | 완료 |
| 더보기 | 새 기능 (BlocksScreen, 서버가 그리는 화면) | `MoreView.swift` | `MoreBlocks.kt` | 완료 |
| 설정 | 설정 및 연결 (SettingsScreen) | `SettingsScreen.swift` | `SettingsScreen.kt` | 완료 |
| 녹음 | 녹음해서 회의록 만들기 (RecordingsScreen) | `RecordingScreens.swift` | `RecordingScreens.kt` | 완료 |
| 녹음 | 녹음 중 빨간 띠 (RecordingBar) | `RecordingScreens.swift` | `RecordingScreens.kt` | 완료 |
| 홈 | 봇 일하는 방식 (BotInstructionsScreen, §16) | `TeamScreens.swift` | `TeamBotInstructions.kt` | 완료 |
| 홈 · 결재 | 사진이 아닌 첨부 파일 줄 (AttachedFiles, §19) | `Attachments.swift` | `design/AttachedFiles.kt` | 완료 |
| 더보기 | 루틴 (RoutinesScreen, §17) | `RoutinesScreen.swift` | `RoutinesScreen.kt` | 완료 |
| 더보기 | 새 기능: 파일 변경 · 알아 둔 사이트·방법 (§19·§20 블록 화면) | `MoreView.swift` (BlocksScreen) | `MoreBlocks.kt` | 완료 |
| 더보기 | 드라이브 (§34): `message`가 있는 줄은 누르면 대화로 요약 부탁 (chevron) | — | `MoreBlocks.kt` | 완료 (화면 확인 전) |
| 캘린더 | 캘린더 여러 개 (§36): 일정마다 캘린더 이름·색, 범례, 추가할 때 캘린더 고르기, 오늘에 캘린더 이름 | — | `CalendarScreen.kt` · `CalendarAddSheet.kt` · `TodaySheet.kt` | 완료 |
| 설정 | 휴대폰 신호 (MacroDroid 대신): 문자·카톡·카드·은행 알림, 위치, 배터리, 걸음 수를 맥의 `/m/v1/ingest`로 | — (아이폰은 단축어) | `signals/` · `PhoneSignalsScreen.kt` · `core/Signals.kt` | 완료 |
| 대화·팀 | 데스크톱 v0.1.50 맞춤 (§37): 버튼 있는 알림·결재 카드를 말풍선으로, 고른 답은 내 말로, 지난 버튼은 흐리게, 작업 | 결재 시트, 글로 팀 만들기, 팀 화면에서 바로 고치기, 봇마다 AI 모델, 팀·봇 방은 접지 않음, 방에서 파일 보내기 | — | `ChatChoices.kt` · `ChatRoomView.kt` · `ChatWorkSheet.kt` · `TeamEdit.kt` · `TeamScreens.kt` | 완료 |
| 새 기능 화면 | 줄 이동 (§38): `rows[].query`가 있는 줄을 누르면 같은 화면을 그 query로 다시 읽음(드라이브 폴더), 뒤로 가기로 한 단계씩 | — | `MoreBlocks.kt` · `Views.kt` | 완료 |
| 결재 | AX 맥: 카드 입력칸·비밀 PIN (CardFormView, §7.3) | `CardPanel.swift` | `design/CardPanel.kt` | 완료 |
| 결재 | AX 맥: 카드에서 고르기 (CardSelectView, §7.3) | `CardPanel.swift` | `design/CardPanel.kt` | 완료 |
| 홈 | AX 맥: 알림 방 · 읽기 전용 방 (views/channels, §7.2), 팀장 표시 | `HomeView.swift`, `ChatRoomView.swift` | `HomeView.kt`, `ChatRoomView.kt` | 완료 |
| 문서 | 목록 하나로: 최근에 쌓인 문서 + 쓰임별(knowledge) 또는 종류별(docgraph) 묶음 | `DocsView.swift` | `DocsView.kt` | 완료 |
| 더보기 | 상태 점검 (HealthScreen, views/health) | `MoreView.swift` | `MoreHealth.kt` | 완료 |
| 홈 | 반응: 좋아요·별로예요 (ReactionButtons, 길게 누르기, feature `reactions`) | `ChatRoomView.swift` | `ChatRoomView.kt` | 완료 |
| 홈 | 사진·파일 보내기 ＋ (feature `share`) | `ChatRoomView.swift` | `ChatRoomView.kt` | 완료 |
| 홈 · 더보기 | 브라우저 화면 넘겨받기 (§22 browser-live) | (아이폰도 같은 요청을 받음) | `BrowserLiveScreen.kt`, `core/BrowserLive.kt` | 완료 |
| 홈 | 방을 그룹 대화방처럼: 얼굴·이름, 연속 글 묶기, 날짜 줄, 답글·작업 스레드 접기, 긴 봇 대화·작업 기록 접기, 반응 수 (§21) | `ChatRoomView.swift`, `RoomFeed.swift` | `ChatRoomView.kt`, `core/RoomFeed.kt` | 완료 |
| 홈 | 방 목록: 맥의 순서·묶음 그대로, 알림 방 없음, 읽음 맞추기 (§21.2·§21.4) | `HomeView.swift`, `AppModel.swift` | `HomeView.kt`, `AppModel.kt` | 완료 |
| 문서 | 3D 우주 없앰 → 최근 고친 문서 + 분야별 폴더, 맥에 먼저 묻는 본문 찾기·첫소리 찾기, 문서 지도 tree (§21.6·§21.7) | `DocsView.swift`, `KoreanSearch.swift` | `DocsView.kt`, `DocsShelf.kt`, `core/KoreanSearch.kt` | 완료 |
| 더보기 | 터미널: Tailscale로 맥에 바로 SSH | `Terminal/*.swift`, `SSH.swift` | `TerminalScreens.kt`, `app/terminal/*`, `core/SSH.kt` | 완료 · 실기기 로그인 확인 필요 |
| 홈 | 비서가 일하는 중: 대화 아래 '생각하는 중…'·'메일 보는 중…' (§4 activity) | (아이폰도 같은 요청을 받음) | `ChatRoomView.kt`, `core/Activity.kt` | 완료 |
| 더보기 › 비서가 챙기는 것 | 프로젝트 가져오기 (§23 projects): 회사별 고르기, 이름·회사 고치기 시트, 회사 이름 바꾸기, 저장하고 바로 읽기/저장만 | `ProjectsScreen.swift` | `ProjectsScreen.kt`, `core/Projects.kt` | 완료 |
| 홈 › 방 목록 | 팀 구조 제안 카드와 팀 구조 화면 (§24 team-plan): 팀·역할·루틴 고치기, 이대로 만들기/만들지 않기, 대화 카드의 "앱에서 고칠게요" | (아이폰도 같은 요청을 받음) | `TeamPlanScreen.kt`, `core/TeamPlan.kt` | 완료 |
| 더보기 › 비서가 챙기는 것 | 처음 알아보기 (§27 first-look): 진행 막대(1초마다), "당신은 이런 사람이군요", 회사·프로젝트·팀 나무, 말로 고치기, 이대로 만들기 | (이 저장소의 아이폰 코드엔 아직 없음) | `FirstLookScreen.kt`, `core/FirstLook.kt` | 완료 |
| 더보기 › 맡긴 일 | 하고 있는 일 (§28 work): 지금 하는 일·최근에 끝난 일, 상태·단계·걸린 시간, 결과 알림(dev.job·browser.task·errands.task·work.interrupted) | (이 저장소의 아이폰 코드엔 아직 없음) | `WorkScreen.kt`, `core/Work.kt` | 완료 |
| 위젯 | 홈 화면 위젯 "오늘과 결재" (AxKitWidget) | `AxKitWidget.swift` | `TodayWidget.kt` | 완료 |
| 공통 | 앱 아이콘 C '봇 팀' (적응형: 파란 그라데이션 배경 + 봇 세 마리, 테마 아이콘은 윤곽) | `AppIcon.appiconset`, `dev/icon/icons.swift` | `res/mipmap-*`, `dev/icon/android-icon.swift` | 완료 |
| 공통 | 토스트 · 전체 정지 띠 · 연결 끊김 띠 | `RootView.swift` | `RootView.kt, design/Components.kt` | 완료 |
| 공통 | 탭 막대 · 결재 숫자 | `RootView.swift` | `RootView.kt` | 완료 |
| 공통 | 봇 캐릭터 (ClayCharacter, BotAvatar) | `ClayCharacter.swift` | `design/ClayCharacter.kt` | 완료 |

## 나란히 보기

### 연결 · 처음 연결 (PairView)
명령어는 `--platform android`, '아이폰'→'휴대폰'  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/0-pair.png" width="200"> | <img src="docs/screenshots/0-pair.png" width="200"> |

### 연결 · QR 찍기 (QRScannerSheet)
VisionKit → CameraX + ZXing. 에뮬레이터 카메라는 검은 화면이라 해석은 단위 테스트(QRDecodeTest)로 확인  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/0b-qr-scanner.png" width="200"> |

### 홈 · 대화 (HomeView + ChatRoomView)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/1-home-chat.png" width="200"> | <img src="docs/screenshots/1-home-chat.png" width="200"> |

### 홈 · 봇 목록 시트 (BotDrawer)
iOS 1b 스크린샷은 예전 홈 화면이라 시트가 안 보임. 밀기·왼쪽 위 버튼·뒤로 가기로 닫기  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/1b-home-bot-sheet.png" width="200"> | <img src="docs/screenshots/1b-home-bot-sheet.png" width="200"> |

### 홈 · 오늘 (TodaySheet)

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1c-today.png" width="200"> |

### 홈 · 말로 입력 (Dictation)
SpeechRecognizer(ko-KR, 가능하면 폰 안에서). 에뮬레이터에 마이크가 없어 화면은 못 찍음. 안드로이드는 말이 멈추면 저절로 끝남  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/1d-dictation.png" width="200"> | (에뮬레이터로 찍을 수 없음) |

### 홈 · 팀 방 · 봇끼리 대화 (RoomHeader)

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1e-team-room.png" width="200"> |

### 홈 · 팀 추가 (AddTeamSheet)

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1f-add-team.png" width="200"> |

### 홈 · 팀 설정 (TeamSheet)

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1g-team-sheet.png" width="200"> |

### 홈 · 팀장회의 방

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1h-meeting-room.png" width="200"> |

### 결재 · 결재 (ApprovalsView + CardPanel)
선택지 누르면 카드 닫힘·숫자 줄어듦·맥의 답 토스트까지 확인  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/2-approvals.png" width="200"> | <img src="docs/screenshots/2-approvals.png" width="200"> |

### 결재 · 사진 있는 결재 (AttachedImages)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/2b-approval-with-picture.png" width="200"> | <img src="docs/screenshots/2b-approval-with-picture.png" width="200"> |

### 결재 · 자동 승인 시트 (AutonomyScreen)

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/2c-auto-approve.png" width="200"> |

### 문서 · 문서 우주 3D (UniverseView)
iOS 3a 스크린샷은 우주 모양 이전 버전. 지금 Swift 코드(어두운 우주·별·빛)를 따름  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/3a-docs-universe.png" width="200"> | <img src="docs/screenshots/3a-docs-universe.png" width="200"> |

### 문서 · 목록 (예전 층별 목록·묶음을 합침)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/3b-docs-list.png" width="200"> | <img src="docs/screenshots/3b-docs-list.png" width="200"> |

### 문서 · 문서 (DocumentScreen)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/3c-document.png" width="200"> | <img src="docs/screenshots/3c-document.png" width="200"> |

### 캘린더 · 캘린더 (CalendarScreen)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/4-calendar.png" width="200"> | <img src="docs/screenshots/4-calendar.png" width="200"> |

### 캘린더 · 일정 추가 (NewEventSheet)
날짜·시간 고르기는 Material 달력·시계 창  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/4b-calendar-add.png" width="200"> | <img src="docs/screenshots/4b-calendar-add.png" width="200"> |

### 더보기 · 더보기 (MoreView)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5-more.png" width="200"> | <img src="docs/screenshots/5-more.png" width="200"> <img src="docs/screenshots/5-more-bottom.png" width="200"> |

### 더보기 · 반복 작업 (JobsScreen)
iOS 5a 스크린샷은 예전 디자인. 지금 Swift 코드를 따름  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5a-jobs.png" width="200"> | <img src="docs/screenshots/5a-jobs.png" width="200"> |

### 더보기 · 지출 관리 (SpendingScreen)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5b-spending.png" width="200"> | <img src="docs/screenshots/5b-spending.png" width="200"> |

### 더보기 · 봇·AI 사용량 (UsageScreen)
막대 그래프는 Canvas로  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5c-usage.png" width="200"> | <img src="docs/screenshots/5c-usage.png" width="200"> |

### 더보기 · AI 연결하기 (ProvidersScreen)
iOS 5d 스크린샷은 예전 디자인  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5d-ai-connect.png" width="200"> | <img src="docs/screenshots/5d-ai-connect.png" width="200"> |

### 더보기 · 맥 상태 (HardwareScreen)
15초마다 새로 읽음  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5e-mac-status.png" width="200"> | <img src="docs/screenshots/5e-mac-status.png" width="200"> |

### 더보기 · 알아서 하는 일 (AutonomyScreen)

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5f-autonomy.png" width="200"> | <img src="docs/screenshots/5f-autonomy.png" width="200"> |

### 더보기 · AI 등급과 용도 (ModelTiersScreen)
순서 바꾸기는 위·아래 버튼(iOS와 같은 방식)  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5g-model-tiers.png" width="200"> | <img src="docs/screenshots/5g-model-tiers.png" width="200"> |

### 더보기 · 새 기능 (BlocksScreen, 서버가 그리는 화면)
text·stat·list·note·ask 블록  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/5h-new-feature.png" width="200"> | <img src="docs/screenshots/5h-new-feature.png" width="200"> |

### 설정 · 설정 및 연결 (SettingsScreen)
안드로이드만: 알림이 꺼져 있으면 '알림 켜기' 줄  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/6-settings.png" width="200"> | <img src="docs/screenshots/6-settings.png" width="200"> |

### 녹음 · 녹음해서 회의록 만들기 (RecordingsScreen)
녹음 → 4MB 조각 올리기 → 맥이 회의록·제안 결재를 만듦까지 에뮬레이터에서 확인  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/7-recording-approvals.png" width="200"> | <img src="docs/screenshots/7-recordings.png" width="200"> <img src="docs/screenshots/7b-recording.png" width="200"> <img src="docs/screenshots/7c-recording-sent.png" width="200"> |

### 녹음 · 녹음 중 빨간 띠 (RecordingBar)
화면이 꺼져도 녹음은 계속(포그라운드 서비스)  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/7b-recording.png" width="200"> |

### 공통 · 봇 캐릭터 (ClayCharacter, BotAvatar)
같은 해시·색·표정·움직임. 서버가 avatar를 주면 그 그림  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1b-home-bot-sheet.png" width="200"> |

### 홈 · 봇 일하는 방식 (§16)
팀 설정에서 봇을 누르면 열려요. 누가 쓴 글인지(기본·AI·직접), 고치기(1500자), 직접 고친 글은 기본 글로 되돌리기. 맥이 거절하면(결재를 건너뛰게 하는 글 등) 맥의 말을 그대로 보여 줘요.  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1g-team-sheet.png" width="200"> <img src="docs/screenshots/1i-bot-instructions.png" width="200"> |

### 홈 · 첨부 파일 (§19)
PDF·한글·엑셀·zip 같은 파일은 이름·크기 줄로 보여요. 누르면 기기 토큰으로 받아 다른 앱(PDF 보기 등)으로 열어요. 맥에서 지워졌으면(404) "파일이 지워졌어요…"라고 말해요.  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/1k-file-in-chat.png" width="200"> <img src="docs/screenshots/1l-file-open.png" width="200"> |

### 더보기 · 루틴 (§17)
새 루틴은 대화로만 만들어요. 여기서는 켜고 끄기, 길게 눌러 지우기(아이폰은 밀어서 지우기), 봇이 나중에 다시 볼 것.  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/5i-routines.png" width="200"> |

### 더보기 · 새 기능: 파일 변경, 알아 둔 사이트·방법 (§19·§20)
서버가 그리는 블록 화면이라 앱 코드 변경 없이 나타나요(아이콘만 추가).  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/5j-file-changes.png" width="200"> <img src="docs/screenshots/5k-ways.png" width="200"> |

### AX(ax-hq) 맥 지원 (§7.2·§7.3)
카드에 입력칸이 오면(긴급 작업 PIN 등) 칸마다 입력하고 보내요. 비밀 칸은 숫자 자판에 가려서 보여요. 맥이 거절하면 그 이유("PIN이 맞지 않아요…")를 그대로 보여 줘요.
고르기 카드는 하나 또는 여러 개를 골라 보내요. 서버가 알려 주는 방 중 봇 방이 아닌 것(결재·장애·로그 …)은 봇 목록 아래 "알림 방"에 나오고, 읽기 전용이면 입력칸 대신 "읽기 전용 방이에요…"가 보여요.
카드 id의 `:`는 한 번만 인코딩해요(두 번 하면 AX에서 410). 가짜 맥에는 이런 카드가 없어서, 화면은 임시로 고친 가짜 맥으로 찍었어요.  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/2d-pin-form.png" width="200"> <img src="docs/screenshots/2e-select-answered.png" width="200"> <img src="docs/screenshots/1m-alert-rooms.png" width="200"> <img src="docs/screenshots/1n-read-only-room.png" width="200"> |

### AX(ax-hq) 서버 기능: 지식 모음, 상태 점검, 반응, 첨부 보내기, 봇끼리 대화, AI 연결
가짜 ax-hq 서버(`ax-hq/.claude/worktrees/ios-app-af3743/ios/dev/ax-hq-mock.mjs`)에 붙여 찍었어요. 모두 서버가 그 기능·뷰를 알려 줄 때만 보여요.
- 문서 탭은 '목록 / 우주' 두 개(목록이 먼저, 우주는 docgraph가 있을 때만). 목록 맨 위에 최근에 쌓인 문서 4개(새것부터, 같은 문서는 한 번) + 안내 한 줄, 그 아래 묶음 카드: views/knowledge가 있으면 쓰임별(회사·프로젝트·기록), 없으면 docgraph의 종류별(색 점). 찾기는 제목과 설명에서. 문서 열기는 docgraph가 있으면 docgraph, 없으면 knowledge.
- 상태 점검: 요약 카드, 문제부터 정렬한 목록, 문제가 있으면 '비서에게 고쳐 달라고 하기'(메인 대화로 문제 목록).
- 반응: 방마다 가장 최근 봇 메시지에 엄지 위·아래 버튼(누른 것은 채워진 아이콘), 봇 메시지를 길게 누르면 좋아요·별로예요·복사.
- 메인 대화 입력칸 왼쪽 ＋: 사진(5장까지, JPEG로 12MB 안으로 줄임)·파일. 12MB를 넘으면 안내만.
- 봇끼리 대화(얼굴·이름 → 받는 봇)와 AI 연결은 ax-hq 모양에서도 그대로 동작해요.  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/9e-knowledge-doc.png" width="200"> <img src="docs/screenshots/9d-health.png" width="200"> |
| | <img src="docs/screenshots/9g-long-press.png" width="200"> |
| | <img src="docs/screenshots/9h-bot-talk.png" width="200"> <img src="docs/screenshots/9j-axhq-ai-connect.png" width="200"> |

### 브라우저 화면 넘겨받기 (§22)
브라우저 심부름이 로그인·확정 버튼에서 멈추면, 비서의 안내 글 아래 "브라우저 화면 열기"(또는 더보기 › 맥 › 브라우저 화면)로 그 페이지를 봐요.
화면을 열면 넘길 페이지가 있으면 바로 `start`, 그림은 `browser-live/stream`(SSE `state`·`frame`)으로 받고 안 되면 `browser-live/frame`을 2초마다.
페이지를 누르면 그 자리(JPEG 픽셀)를 누르고, 위아래로 끌면 페이지를 내려요. 칸을 누른 뒤 아래 칸에 쓰고 "넣기"로 한 번에 보내요(쓴 글자는 앱 기록에도 안 남김).
뒤로·지우기·다음 칸·엔터 버튼, "다 했어요"를 누르면 닫고 대화로 돌아가 비서가 이어서 해요. 가짜 맥에 이 기능이 없어서 임시로 고친 가짜 맥(로그인 화면 그림)으로 찍었어요.  

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/10a-browser-handoff.png" width="200"> <img src="docs/screenshots/10b-browser-live.png" width="200"> <img src="docs/screenshots/10c-browser-after.png" width="200"> <img src="docs/screenshots/10e-browser-resumed.png" width="200"> <img src="docs/screenshots/10f-browser-closed.png" width="200"> |

### 아이폰 최신 변경 따라잡기 (§21, 터미널)
가짜 ax-hq 서버로 찍었어요. 실제 일 내용이 보이는 화면(아이폰 스냅숏 포함)은 공개용으로 뺐어요.

| 아이폰 | 안드로이드 |
|---|---|
| | <img src="docs/screenshots/11c-team-room.png" width="200"> |
| | <img src="docs/screenshots/12b-docs-search.png" width="200"> <img src="docs/screenshots/12d-health.png" width="200"> |
| (터미널 스크린샷 없음) | <img src="docs/screenshots/13a-terminal-hosts.png" width="200"> <img src="docs/screenshots/13b-terminal-add.png" width="200"> <img src="docs/screenshots/13c-terminal.png" width="200"> |

터미널은 이 맥의 실제 SSH 서버에 붙어 맥의 열쇠를 기억하고, 휴대폰 열쇠가 등록되지 않았다는 안내까지 확인했어요. 로그인해서 셸을 쓰는 것은 휴대폰 공개키를 맥의 `~/.ssh/authorized_keys`에 넣은 뒤 실제 폰에서 확인해야 해요.
문서 지도 3D 화면(우주)은 아이폰처럼 없앴어요. 위의 예전 '우주' 스크린샷은 기록으로만 남아 있어요.

### 비서가 일하는 중 (§4 activity)
서버가 `activity`를 알려 줄 때만. 말을 보내면 그 방 대화 아래에 점 세 개와 "생각하는 중…", 도구를 쓰는 동안은 "메일 보는 중…"처럼 단계가 보이고, 답이 오거나 `idle`이면 사라져요. 3분 동안 소식이 없으면 앱에서도 지워요. 가짜 맥에 이 기능이 없어 임시로 고친 가짜 맥으로 찍었어요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/14b-working.png" width="200"> <img src="docs/screenshots/14c-answered.png" width="200"> |

### 위젯 · 홈 화면 위젯 "오늘과 결재"
앱이 쓴 내용만 읽고 맥에 직접 붙지 않아요(아이폰과 같음). 결재가 있으면 누르면 결재 탭, 없으면 홈. 좁게 줄이면 결재만 보여요.  

| 아이폰 | 안드로이드 |
|---|---|
| (아이폰 스크린샷 없음) | <img src="docs/screenshots/8-widget.png" width="200"> |

### 공통 · 앱 아이콘
아이폰 아이콘 C(파란 바탕, 앞에 하얀 팀장 봇, 뒤에 초록·주황 직원 봇)를 안드로이드 적응형 아이콘으로 나눴어요. 배경은 #4A82FF → #2456E8, 앞 레이어는 봇 세 마리(안전 영역 안), 테마 아이콘은 봇 윤곽만.  

| 아이폰 | 안드로이드 |
|---|---|
| <img src="docs/screenshots/ios/icon-1024.png" width="120"> | <img src="docs/screenshots/8b-app-icon.png" width="300"> |


### 프로젝트 가져오기 (§23 projects)
아이폰(8160fde)과 같은 모양이에요. 서버 기능에 `projects`가 있을 때만 더보기 › 비서가 챙기는 것에 보여요.
- **맨 위:** 읽는 상태("다 읽었어요" / "읽는 중 · 프로젝트 읽는 중"), 고른 수, 개수(저장소·파일·문서·대화…), "처음 읽을 때는 몇십 분에서 몇 시간" 안내.
- **회사 카드:** 회사마다 하나예요. "1 / 2개 고름"과 "이름 바꾸기"가 있어요.
- **프로젝트 줄:** 체크, 이름, 경로(고정폭 글씨), "2시간 전 작업 · Claude 12 · Codex 3", 연필 버튼이 있어요. 연필을 누르면 '프로젝트 고치기' 시트에서 이름을 고치거나 다른 회사로 옮겨요.
- **못 찾은 폴더:** 주황 글씨로 "빼기만 할 수 있어요"라고 나와요.
- **저장:** 고른 전체 목록을 보내요. 400의 `message`는 그대로 보여요. 아래로 당기면 `refresh`예요.

| 아이폰 | 안드로이드 |
|---|---|
| (스크린샷 없음) | <img src="docs/screenshots/15a-projects.png" width="200"> <img src="docs/screenshots/15b-projects-edit.png" width="200"> |

### 팀 구조 (§24 team-plan)
서버 기능에 `team-plan`이 있고 제안서가 있을 때만 보여요.
- **방 목록 카드:** 방 목록(봇) 맨 위에 "팀 구조 제안" 카드가 떠요.
  - 오너가 정해야 할 때(`proposed`)는 파란 테두리와 점으로 눈에 띄게 보여요.
  - 만든 뒤에는 "만들었어요", 버린 뒤에는 "만들지 않기로 했어요"로 남아요.
  - 대화창에 새 제안서 카드가 오면 방 목록 카드도 바로 새로 받아요.
- **화면:** 회사별 팀 카드예요. 팀마다 이름, 목적, "개발 · 검수 포함", 직원 역할(이름과 하는 일), 루틴(언제, 제목, 할 일)이 보여요. `tasks`는 "반복하는 일" 참고 줄로만 보여요.
- **고치기(`proposed`일 때만):**
  - 팀 빼기, 이름·목적·회사 고치기.
  - 역할 더하기·고치기·빼기(최대 6개, 이름 1~20자).
  - 루틴 고치기·빼기(매일 / 매주+요일 / 몇 시간마다, 시각은 09:30처럼).
- **저장과 만들기:**
  - 저장은 고친 전체 목록을 `save`로 보내요.
  - "이대로 만들기"는 고친 것을 먼저 저장하고 `apply`를 보내요. 그다음 "팀 N개와 루틴 M개를 만들었어요"를 보여 주고 봇 목록을 새로 받아요.
  - "만들지 않기"는 한 번 더 물은 뒤 `decline`을 보내요.
  - 400의 `message`는 버튼 위에 빨간 글씨로 그대로 보여요.
- **대화 카드:** "앱에서 고칠게요"(`fix-in-app`, 서버 870e742)를 누르면 답을 보내지 않고 이 화면을 열어요. 카드는 열린 채로 남아요. 그 전 서버의 `edit` id도 같은 방식으로 처리해요(서버가 `edit`를 옛 '고치기', 즉 아니요로 받아 제안서를 버리던 문제 때문이에요).

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/16a-team-plan-card.png" width="160"> <img src="docs/screenshots/16b-team-plan.png" width="160"> <img src="docs/screenshots/16c-team-plan-routine.png" width="160"> <img src="docs/screenshots/16d-team-plan-built.png" width="160"> <img src="docs/screenshots/16e-team-plan-refused.png" width="160"> |

### 처음 알아보기 (§27 first-look)
서버 기능에 `first-look`이 있을 때만 더보기에 보여요. 터미널(`ax-kit onboarding status`)과 같은 말을 써요.
- 읽는 동안: 지금 읽는 단계, 막대와 % · 걸린 시간, `폴더 파일 120/400 · …`. 1초마다 새로 읽어요(읽는 동안만).
- 끝나면: "당신은 이런 사람이군요", "이런 조직 분류가 필요해 보여요" 아래 회사(관계) › 프로젝트 › 팀·역할 › 루틴.
- 제안서가 아직 만들기 전이면 말로 고치기(`revise`, 서버 답 한 줄을 그대로), 이대로 만들기(`apply`), 팀 구조 화면으로 가기.
- 가짜 맥: '알아보기 시작'을 누르면 20초쯤 읽는 척하고 끝나요. "블로그 빼"처럼 말하면 그 팀을 빼요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/17a-first-look-running.png" width="200"> <img src="docs/screenshots/17b-first-look-result.png" width="200"> <img src="docs/screenshots/17c-first-look-revise.png" width="200"> |

### 하고 있는 일 (§28 work, 서버 53ea11e)
뷰 `work`가 있을 때만 더보기 › 맡긴 일에 보여요. 하는 일이 있는 동안 10초마다 새로 읽어요. 요약은 웹 페이지 글이 섞일 수 있어서 글자로만 보여 줘요.
대화창의 결과 알림(`dev.job` 등)은 눈에 띄는 카드로 보이고 "하고 있는 일 보기 ›"가 붙어요. `live`가 있으면 '브라우저 화면 열기'도 붙어요. 앱을 안 볼 때는 급한 알림 채널로 알려요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/18a-work.png" width="200"> <img src="docs/screenshots/18b-work-result-notice.png" width="200"> |

### 받아쓰기 (§30 dictation, 서버 d2f1bc2)
서버 기능에 `dictation`이 있을 때만 입력창 옆에 맥 마이크 버튼이 생겨요. 없으면 예전처럼 휴대폰 음성 인식으로 받아써요.
- 누르면 녹음(AAC m4a 모노 16kHz), 다시 누르면 멈추고 `POST dictation`으로 보내요. 받은 글은 입력창 커서 자리에 넣고, 보내지는 않아요.
- 녹음 중에는 시간과 소리 크기 막대가 보여요. 3분이 되면 저절로 멈춰요. 1초보다 짧으면 "너무 짧아요". 처음부터 끝까지 소리가 거의 없으면 맥에 보내지 않고 마이크를 확인해 달라고 해요.
- 맥이 글로 바꾸는 동안 버튼이 돌아가요. 그때 누르거나 '취소'를 누르면 요청을 멈추고 아무것도 넣지 않아요. 400·413·503·500이 오면 맥이 준 말을 그대로 보여요. 녹음 파일은 보내고 바로 지워요.
- 보내기 버튼은 늘 오른쪽에 있어요(비어 있으면 회색). 그래서 마이크를 한 번 더 눌러 말을 더 넣을 때 실수로 보내지 않아요.
- 에뮬레이터의 마이크에는 맥 소리가 들어오지 않아요. 그래서 글이 들어가는 사진은 "소리 없음" 확인만 잠깐 끈 시험용 빌드로 찍었어요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/20a-dictation-recording.png" width="160"> <img src="docs/screenshots/20b-dictation-writing.png" width="160"> <img src="docs/screenshots/20c-dictation-text.png" width="160"> <img src="docs/screenshots/20d-dictation-error.png" width="160"> |

### 사람 (§29 people, 서버 d2f1bc2)
뷰 `people`이 있을 때만 더보기 › 비서가 챙기는 것 › 사람이 보여요.
- 카드: 이름, 직함·회사, 다른 이름, 관계, 메모(줄마다), 열린 약속 수, 메일 주소 수, 마지막 연락, 고친 때.
- 이름·다른 이름·회사·관계·직함·메모로 찾기(띄어쓰기와 대소문자는 무시해요).
- 고치기: 직함·회사·관계·메모. 바뀐 칸만 보내고(`views/people/edit`), 비운 칸은 `""`로 보내서 지워요. 400이면 맥이 준 말을 보여요. 고친 카드는 맨 위로 가요.
- 지우기와 합치기는 "대화로 말해 주세요"라고 안내해요. 카드가 없으면 "대화에서 사람을 소개하면 여기에 카드가 생겨요".

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/21a-people.png" width="200"> <img src="docs/screenshots/21b-people-edit.png" width="200"> <img src="docs/screenshots/21c-people-saved.png" width="200"> |

### 작업 시트와 @ 봇 이름 (§31, 서버 b087565)
뷰 `work`가 있으면 모든 대화 화면의 입력창 위에 접힌 한 줄 "하는 중 N개 · 맨 위 일"이 보여요. 하는 일이 없으면 숨어요.
- 누르면 아래에서 시트가 올라와요. 하는 중(`queued`·`running`·`waiting`)이 위, 최근 끝난 일 5개가 아래예요. 상태·단계·걸린 시간을 보여 줘요.
- `parent`가 있는 하위 작업은 부모 바로 아래에 들여 써요. 부모가 끝났어도 하위가 돌면 부모는 위에 남아요.
- 일을 누르면 `origin.address`의 방으로 가요. `bot:<id>`는 그 봇 방, `owner`는 비서 대화예요.
- 하는 중인 일이 있으면 화면이 보일 때 15초마다 `views/work`를 다시 읽어요. 없으면 새 알림(notice)이 올 때 다시 읽어요.
- 입력창에 `@`를 치면 봇 이름이 떠요(팀장 먼저, 초성도 돼요: "ㅈㄱ"). 고르면 "@봇이름 "이 들어가요. 메일 주소의 @는 무시해요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/22a-work-strip.png" width="160"> <img src="docs/screenshots/22b-work-sheet.png" width="160"> <img src="docs/screenshots/22d-work-open-room.png" width="160"> <img src="docs/screenshots/22e-mention.png" width="160"> <img src="docs/screenshots/22f-mention-picked.png" width="160"> |

### 앱 업데이트 (§32 app-updates, 서버 68a9f29)
스토어 없이 맥이 만든 새 버전으로 바꿔요. 서버 기능에 `app-updates`가 있을 때만 해요.
- 앱을 켤 때와 하루에 한 번 `views/app-updates`를 읽어요. `android.build`가 이 앱의 versionCode보다 크면 홈 위에 "새 버전이 있어요 · 바꾸기"가 떠요. 설정 화면에도 같은 칸이 있어요.
- 바꾸기를 누르면 `app-updates/android.apk`를 캐시에 받고(진행 %), sha256을 확인해요. 다르면 지우고 쉬운 말로 알려요. 맞으면 PackageInstaller로 깔아요. 시스템이 "업데이트하시겠습니까?"를 묻고, 대화와 연결은 그대로예요.
- 처음 한 번은 "출처를 알 수 없는 앱"을 허용해야 해요. 설정 열기 버튼이 그 화면을 열고, 돌아오면 설치를 이어서 해요.
- 빌드: `AXKIT_KEYSTORE`·`AXKIT_KEYSTORE_PASSWORD`·`AXKIT_KEY_ALIAS`가 있으면 그 키(PKCS12)로 release 서명해요. 없으면 예전처럼 debug 키예요. `AXKIT_VERSION_CODE`·`AXKIT_VERSION_NAME`이 있으면 그 번호를 써요.
- 에뮬레이터: 시험용 키로 만든 release 77을 깔고, 가짜 맥(`--apk v78.apk --apk-build 78`)이 준 78로 바꿨어요. 연결이 그대로인 것까지 확인했어요.
- 같이 고친 것: release 빌드가 켜자마자 죽었어요(R8이 WorkManager의 데이터베이스 클래스를 지움). `proguard-rules.pro`에 keep 규칙을 넣었어요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/23a-update-banner.png" width="160"> <img src="docs/screenshots/23b-update-permission.png" width="160"> <img src="docs/screenshots/23c-update-confirm.png" width="160"> <img src="docs/screenshots/23d-update-broken.png" width="160"> <img src="docs/screenshots/23e-update-settings.png" width="160"> |

### 캘린더 여러 개 (§36, 서버 55d61ee)
새 필드는 모두 선택이에요. 예전 맥이 아무것도 안 보내면 화면도 예전과 같아요.
- 일정마다 아래에 캘린더 이름(계정이 둘 이상이면 계정 주소도)과 그 캘린더 색 점이 보여요. 달력 칸의 점도 그날 일정이 있는 캘린더 색이에요(최대 3개).
- 범례는 `calendars`로 만들어요(둘 이상일 때). 기본 캘린더는 늘 파란색이에요. 나머지는 캘린더 id로 색을 정하고, 겹치면 다음 빈 색으로 옮겨요. 그래서 목록이 같으면 언제나 같은 색이에요. "한 일"의 초록은 따로 남겨 뒀어요.
- 일정 추가: 쓸 수 있는(`writable`) 캘린더만 보여요. 기본 캘린더가 맨 위이고 처음부터 골라져 있어요. 쓸 수 있는 캘린더가 하나뿐이면 고르는 칸이 없어요. 고른 id를 `calendar`로 보내고, 답의 `calendar`로 "'모아카페' 캘린더에 넣었어요"라고 알려요.
- `calendarsLimited`가 오면 범례 밑에 "기본 캘린더만 보여요 — 비서 맥에서 ax-kit connect google을 한 번 더 해 주세요" 한 줄이 보여요.
- 오늘 화면: 일정 제목 밑에 캘린더 이름이 보여요.
- 가짜 맥: `/dev/calendars?mode=many|one|limited|none`으로 바꿔 볼 수 있어요.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/24a-calendars-legend.png" width="160"> <img src="docs/screenshots/24b-calendar-picker.png" width="160"> <img src="docs/screenshots/24c-calendars-limited.png" width="160"> <img src="docs/screenshots/24d-today-calendar.png" width="160"> |

### blocks 화면의 줄 이동 (§38, 서버 962456b)
- `rows[].query`가 있는 줄은 눌러서 같은 화면을 `views/<name>?<query>`로 다시 읽어요(폴더 열기). `{}`는 처음 화면. `message`와 같이 오면 `query`가 먼저예요.
- 연 query를 쌓아 두고, 휴대폰의 뒤로 가기와 왼쪽 위 화살표가 한 단계씩 올라가요. 처음 화면에서 뒤로 가면 화면을 닫아요. 화면을 돌려도 위치가 남아요.
- 드라이브: "📁 폴더로 보기" → 계정 → 내 드라이브 / 공유 문서함 → 폴더 → 파일(누르면 요약 부탁). 가짜 맥에 같은 폴더 트리를 넣었어요.
- 메일 화면(`views/mail`)은 blocks 화면이라 앱을 바꾸지 않아도 맥이 알려 주면 더보기에 떠요.

<img src="docs/screenshots/27-drive-folders.png" width="200">

### 데스크톱 v0.1.50에 맞춤 (§37, 서버 c7af535)
- 대화: 버튼이 있거나 두 줄 넘는 비서 방 알림은 비서 말풍선 하나, 버튼은 그 안에. 버튼은 숨기지 않고 남아요. 가장 최근 버튼만 누를 수 있고, 내가 다음 말을 하면 지난 버튼은 흐려져요. 고른 버튼은 진하게, 그 글은 바로 내 말로 보여요.
- 결재 카드도 대화에서는 말풍선: "결재 요청" 칩, 질문과 이유, 버튼. 답하면 "결재 끝 · 승인했어요"로 바뀌고 버튼이 흐려지며 고른 답이 내 말로 붙어요. 입력 칸·선택 메뉴가 있는 카드는 "결재에서 답하기 ›".
- 대화창 위 줄: 하는 일과 "결재 N" 빨간 배지. 누르면 작업 | 결재 시트(결재 탭에서 바로 답하기). 하고 있는 일 화면이 없는 맥이면 결재만.
- 팀·봇 방: 모든 대화를 차례대로(접기·답글 묶기 없음). 비서 방은 예전처럼 작업 기록을 접어요. ("짧게 보기" 스위치는 아직 없어요.)
- 팀 추가: "글로 설명하기"(기본) — 큰 칸 하나에 적으면 `createTeamFromText`로 팀을 만들고, 만든 팀(이름·하는 일·팀원과 역할, 안내문을 팀장 지시로 넣었는지)을 보여 줘요. "직접 정하기"는 예전 양식.
- 팀 설정: 팀 이름·하는 일 "고치기"(`renameTeam`), 팀원마다 "고치기"로 이름·역할(`editBot`)과 다른 팀으로 옮기기(`moveBot`, 팀장은 안 됨). 맥이 이 액션을 알 때만 보여요.
- 봇마다 쓰는 AI: `views/bots`의 `model`을 등급 옆에("등급 B · GPT-5 mini · OpenAI"), 팀 위에는 팀이 쓰는 AI.
- 팀·봇 방에서도 사진·파일 보내기: `capabilities.features`에 `share-rooms`가 있을 때만, `x-channel` 헤더로 그 방에.
- 가짜 맥: 새 액션 네 개, 봇마다 `model`, `share-rooms`, `/dev/digest`(번호 버튼이 붙은 저녁 알림).
- 에뮬레이터에서 확인: 알림 말풍선과 버튼, 버튼을 누르면 내 말로 보이고 나머지는 흐려짐, 결재 카드 말풍선에서 승인, 결재 시트, 팀 방에서 파일을 보내면 그 방에 도착, 팀 이름 바꾸기, 팀원을 디자인팀으로 옮기기, 글로 팀 만들고 방 열기.

| 아이폰 | 안드로이드 |
|---|---|
| (아직 없음) | <img src="docs/screenshots/26a-notice-bubble.png" width="160"> <img src="docs/screenshots/26b-picked-as-my-line.png" width="160"> <img src="docs/screenshots/26c-card-bubble-answered.png" width="160"> <img src="docs/screenshots/26d-approvals-sheet.png" width="160"> <img src="docs/screenshots/26e-team-room-all-lines.png" width="160"> <img src="docs/screenshots/26f-team-models.png" width="160"> <img src="docs/screenshots/26g-edit-member.png" width="160"> <img src="docs/screenshots/26h-team-from-text.png" width="160"> |

### 휴대폰 신호 (MacroDroid 대신)
설정 › 휴대폰 신호. 휴대폰이 보는 것을 짝지은 맥의 `POST /m/v1/ingest`(같은 주소, 같은 토큰)로 보내요. 맥이 나머지를 해요: 카드·은행 알림 → 가계부, 인증번호 → 브라우저 자동 로그인, 택배·예약 문자 → 할 일, 위치 → 점심·이동 시간·날씨·집/회사. 모든 종류가 처음엔 꺼져 있어요.
- 알림 읽기(`NotificationListenerService`): 문자 권한 없이 메시지 앱 알림으로 문자를 읽어 `sms`(보낸 사람 = 알림 제목)로, 그 밖의 앱은 `notification`(앱 이름·제목·본문 200자)로 보내요. 계속 떠 있는 알림·묶음 요약·시스템 알림·ax-kit 자신의 알림·10분 안의 같은 알림은 건너뛰어요. 보낼 앱: "모든 앱" 또는 고른 앱(처음엔 메시지, 카톡, 카드·은행·페이 앱 + 이름에 카드/은행/페이가 들어간 앱).
- 위치: Google Play의 fused location, 균형 전력, 30분 간격·250m 이상 움직일 때(앱이 닫혀 있어도 `PendingIntent`로 받음). 휴대폰이 장소 이름을 주면 "서울특별시 강남구 삼성동"처럼 붙여요. 같은 위치는 한 번만.
- 배터리: 20% 아래로 내려가면 한 번, 30% 위로 충전되면 다시 준비. 걸음 수: Health Connect의 오늘 합계를 한 시간에 한 번, 바뀌었을 때만.
- 보내기: 한 번에 50개까지, 맥에 안 닿으면 휴대폰에 모아 두고 WorkManager가 다시 보내요(화면이 꺼져도). 각 항목은 생긴 시각(`at`)을 가지고, 47시간이 지나면 버려요. 맥이 한 묶음을 거절하면 하나씩 다시 보내서 나쁜 하나가 나머지를 막지 않아요. 다시 켜거나 앱을 업데이트하면 위치 받기를 다시 걸어요.
- 화면: 권한마다 무엇에 쓰는지와 켜기 버튼(알림 접근, 위치 항상 허용, 배터리 최적화 제외), 종류마다 스위치와 마지막으로 보낸 때, 못 보낸 개수, "지금 보내 보기"(배터리 잔량, 켜 두었다면 위치·걸음 수). '제한된 설정' 안내도 있어요.
- 개인정보: 짝지은 맥에만 보내요. 휴대폰에는 다시 보낼 것만 잠깐 남고, 연결을 끊으면 지워요.
- 가짜 맥: `/m/v1/ingest`가 진짜와 같은 규칙으로 받아요. `/dev/ingest`로 받은 것을 보고, `?fail=1`이면 503으로 거절해요.
- 에뮬레이터에서 확인: 카드 승인 문자·인증번호 문자가 0.1초 안에 `sms`로 도착, 맥이 안 받을 때 모아 뒀다가 약 30초 뒤 원래 시각으로 다시 보냄, 고르지 않은 앱 알림은 안 보내고 "모든 앱"이면 보냄, 위치에 장소 이름, 배터리 15%에서 한 번만. 계속 떠 있는 알림 건너뛰기는 에뮬레이터에서 만들 수 없어 코드로만 확인했어요.

| 아이폰 | 안드로이드 |
|---|---|
| (단축어로 보냄) | <img src="docs/screenshots/25a-phone-signals.png" width="160"> <img src="docs/screenshots/25b-signal-apps.png" width="160"> <img src="docs/screenshots/25c-phone-signals-test.png" width="160"> |

### 앱 리뷰 뒤 고친 것 (2026-11-23)
- 대화와 읽음 표시를 휴대폰에 저장해요(`timeline.json`, 방마다 최근 300줄). 다시 켜면 저장된 곳부터 이어 받아요. 다른 기기에서 읽은 것(`views/channels`의 `readSeq`)도 반영해요.
- 앱을 안 볼 때도 15분마다 새 결재·비서 말·급한 알림(스미싱 의심, 기차 빈자리)을 알림으로 보여요(WorkManager). 알림의 승인/거절은 선택지가 없는 카드면 `decision`으로 보내고, 실패하면 "처리하지 못했어요" 알림을 띄워요.
- 보내기: 거절된 말풍선은 빨간 글씨로 남고 눌러서 다시 보내기·복사·지우기. 맥이 바쁘면(429/5xx) 2·4·8…30초 뒤 다시, 5번 넘으면 "보내지 못했어요". 맥이 받은 말풍선은 자기 줄이 올 때까지 남아서 깜빡이지 않아요. 빠른 답도 같은 길로 가고, 하나 고르면 그 줄 버튼은 끝나요. 8000자가 넘으면 나눠 보내요. 다시 짝지으면 보낼 것 목록을 비워요.
- 연결: 스트림이 두 개 뜨지 않게 했고, 멈추면 소켓도 닫아요. 짧게 끊기면 노란 "다시 연결하는 중", 계속 안 되면 빨간색. 네트워크가 돌아오면 바로 다시 붙어요. 다시 붙을 때마다 기능·방 목록을 새로 읽고, 맥의 기록이 처음부터 다시 시작됐으면 대화도 처음부터 받아요.
- 대화 화면: 바닥 근처에 있거나 내가 보냈을 때만 아래로 내려가요. 아니면 "새 메시지 n개 ↓"가 떠요. 최신 줄이 보일 때만 읽음 처리해요.
- 녹음 올리기: 맥이 받은 개수부터 다시 보내고, 맥이 다 받았다고 할 때만 오디오를 지워요. 거절되면 녹음은 남고 다시 보내기·지우기를 할 수 있어요.
- 그 밖에: 공유 시트 글에 `shared:true`, 팀 구조 저장 때 앱이 모르는 필드도 그대로 돌려보냄, 같은 카드가 다시 오면 맨 아래로, 위임 줄에 넘긴 팀장 이름, 오늘 시트에 "답을 기다리는 것"과 "오늘 쓴 돈", "앞으로는 알아서"는 승인할 때만.

<img src="docs/screenshots/17d-urgent-notice.png" width="200">

### 2차 리뷰 뒤 고친 것 (2026-11-23)
- 알림: 앱 밖에서 온 notice는 kind가 없어도 다 알려요(맥이 이미 조용한 시간·한도를 적용함). kind는 급한 채널과 ⚠️만 정해요. 방마다 알림 하나(MessagingStyle, 최근 5줄, 처음만 소리)이고 notice는 따로 떠서 덮이지 않아요. 숨긴 방·팀장회의·봇끼리 대화·이미 읽은 방은 건너뛰고, 한 시간에 30개까지. 방을 열거나 다른 기기에서 읽으면 그 방 알림이 지워져요. 알림에서 바로 답하기(보낼 것 목록을 거쳐요).
- 항상 연결해 두기(설정, 기본 꺼짐): 앱을 닫아도 연결을 열어 두고 바로 알려요. 진짜 푸시가 생기기 전까지 쓰는 방법이에요.
- 앱 안: 다른 방에 급한 알림이나 맡긴 일 결과가 오면 위에 띠가 떠요. 방에 들어가면 "여기까지 읽었어요" 줄이 보여요.
- 연결: 와이파이 ↔ LTE처럼 네트워크가 바뀌면 바로 다시 붙어요. 새 프로세스에서 도는 백그라운드 확인은 기능·방 목록을 먼저 읽어요(그전엔 위젯·팀 구조를 건드리지 않아요).
- 저장: 대화 저장은 한 번에 하나만 쓰고, 쓰는 동안 바뀌면 다시 써요. 파일은 백그라운드에서 읽고, 방마다 150줄·전체 크기 상한, 숨긴 방은 저장 안 해요. 고른 빠른 답과 👍/👎도 저장돼요.
- 보내기: 맥에 안 닿아도 새 말풍선이 바로 떠요(목록 잠금을 네트워크 동안 잡지 않음). 결재 답도 맥에 안 닿으면 모아 뒀다가 보내요("맥에 닿으면 답을 보낼게요"). 실패하거나 지운 빠른 답은 버튼이 다시 살아나요.
- 그 밖에: 처음 알아보기·하고 있는 일은 화면이 보일 때만 새로 읽어요. 앱이 뒤에 있으면 재시도·읽음 처리를 하지 않아요. bad_option은 닫힌 것으로 치지 않아요. 하고 있는 일에 "어디서 시킨 일"을 보여요.

<img src="docs/screenshots/19a-room-notification.png" width="200"> <img src="docs/screenshots/19b-read-line-and-reply.png" width="200"> <img src="docs/screenshots/19c-always-on.png" width="200">

## 진짜 ax-kit 서버로도 확인

ax-kit 서버 저장소의 앱 API(폰 다리 + 앱 채널)를 임시 폴더·메모리 비밀값으로 띄워(키체인·디스코드·AI 키 안 씀) `--platform android`로 짝을 맞췄어요.
연결, 보내기(스트림으로 되돌아옴), 더보기의 모든 화면(알아서 하는 일, 반복 작업, 사용량, AI 등급, 맥 상태, 할 수 있는 일, 이번 주 한 일), 캘린더, 문서(지도 없음 안내)가 오류 없이 열렸어요.

| 보낸 글 | 더보기 | AI 연결하기 |
|---|---|---|
| <img src="docs/screenshots/real-server/real-2-chat.png" width="180"> | <img src="docs/screenshots/real-server/real-3-more.png" width="180"> | <img src="docs/screenshots/real-server/real-4-providers.png" width="180"> |

§18 메일 도착 처리와 §20 `web.do`는 화면이 따로 없어요. 알림과 결재 카드로 오므로 지금 화면 그대로 보여요.

## 안드로이드 방식에 맞춘 것

- 뒤로 가기 제스처: 화면 닫기, 봇 목록 닫기, 방에서 나가기, 시트 닫기.
- 공유: 시스템 공유 시트의 "ax-kit"(`ShareActivity`). 글·링크는 보내기 대기열로(맥에 닿지 않으면 나중에), 사진·파일은 `share`로.
- 알림 허락: 안드로이드 13 이상은 연결 뒤 한 번 물어봄. 결재 알림에 승인/거절 버튼.
- 토큰: 키체인 대신 Android Keystore(AES-256-GCM, 기기 밖으로 못 나감), 앱 백업 꺼짐.
- 녹음: 화면이 꺼져도 계속되도록 마이크 포그라운드 서비스("녹음 중" 알림).
- 루틴 지우기: 아이폰의 밀어서 지우기 대신 길게 눌러 메뉴(접근성 동작도 있음).
- 말: '아이폰'·'키체인'처럼 안드로이드에 틀린 말만 '휴대폰'·'이 휴대폰 안에만 잠가서'로 바꿈.

## 아직 남은 것

- **푸시**: ax-kit 서버는 APNs(애플)만 보내요. 안드로이드 알림은 앱이 맥과 이어져 있는 동안 온 결재만 떠요. 앱이 꺼져 있어도 받으려면 서버에 FCM이 필요해요.
- **터미널 로그인**, **말로 입력**과 **QR 카메라**는 실제 폰에서 한 번 확인이 필요해요(에뮬레이터에 마이크·카메라 장면이 없음).

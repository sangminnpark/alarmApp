# 🎙️ Voice Wake (보이스 웨이크)

**Voice Wake**는 AI 기술을 활용하여 당신이 사랑하는 목소리나 원하는 메시지로 아침을 깨워주는 스마트 알람 애플리케이션입니다. 단순한 기계음이 아닌, ElevenLabs와 Google TTS를 통한 자연스러운 음성 안내를 경험해 보세요.

---

## ✨ 주요 기능 (Key Features)

### 1. AI 커스텀 보이스 알람
*   **ElevenLabs 연동**: 직접 녹음한 음성 파일을 등록하여 세상에 하나뿐인 나만의 AI 목소리로 알람을 설정할 수 있습니다.
*   **Google Cloud TTS**: 다양한 국적과 성별의 고품질 구글 보이스를 기본 제공합니다.
*   **실시간 시간 안내**: 알람이 울리는 도중에도 1분마다 현재 시각을 감지하여 "현재 시간은 X시 X분입니다"라고 정확하게 읽어줍니다.

### 2. 세련된 사용자 경험 (UX)
*   **Material 3 디자인**: 최신 안드로이드 디자인 가이드를 준수하여 파스텔 톤의 깔끔하고 부드러운 UI를 제공합니다.
*   **페이드 인(Fade-in) 효과**: 소리가 아주 작게 시작하여 서서히 커짐으로써 갑작스러운 소음에 놀라지 않도록 도와줍니다.
*   **알림 모드 개별 설정**: 각 알람별로 사운드, 진동, 무음을 자유롭게 조합하여 설정할 수 있습니다.

### 3. 고도화된 시스템 로직
*   **자동 재예약(Reschedule)**: 반복 요일 설정 시, 알람 종료 후 자동으로 다음 알람을 계산하여 시스템에 등록합니다.
*   **배터리 최적화 대응**: 정확한 시간에 알람이 울릴 수 있도록 전력 관리 예외 처리가 되어 있습니다.
*   **데이터 안전성**: API 호출 실패 시 즉시 시스템 기본 TTS로 전환되는 이중 방어 로직이 구현되어 있습니다.

---

## 🛠 기술 스택 (Tech Stack)

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Networking**: Retrofit2, OkHttp3
*   **Async Processing**: Coroutines, Flow
*   **Local Storage**: SharedPreferences, GSON
*   **Media**: Android MediaPlayer, TextToSpeech, Vibrator
*   **Architecture**: Feature-based Package Structure

---

## 📂 프로젝트 구조 (Project Structure)

```text
com.example.activity_mainxml
├── alarm          # 알람 예약, 수신(Receiver) 및 재부팅 대응 로직
├── data           # API 통신(Retrofit), 보이스 및 알람 저장소
├── model          # AlarmItem, TtsModels 등 데이터 클래스
├── ui
│   ├── alert      # 알람 발생 시 나타나는 화면 (TTS 재생, Fade-in)
│   ├── main       # 메인 알람 리스트 및 항목 디자인
│   ├── edit       # 알람 추가/수정 및 보이스 등록 다이얼로그
│   └── theme      # 디자인 시스템 (Color, Type, Theme)
└── util           # 파일 저장 및 공통 유틸리티
```

---

## 🚀 시작하기 (Getting Started)

이 프로젝트를 실행하려면 `local.properties` 파일에 다음과 같은 API 키 설정이 필요합니다.

```properties
GOOGLE_API_KEY=YOUR_GOOGLE_CLOUD_TTS_API_KEY
ELEVEN_LABS_API_KEY=YOUR_ELEVENLABS_API_KEY
```

---


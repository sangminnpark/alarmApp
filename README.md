# 🎙️ Voice Wake (보이스 웨이크)

**Voice Wake**는 AI 기술을 활용하여 당신이 사랑하는 목소리나 원하는 메시지로 아침을 깨워주는 스마트 알람 애플리케이션입니다. 단순한 기계음이 아닌, ElevenLabs와 Google TTS를 통한 자연스러운 음성 안내를 경험해 보세요.

---

## ✨ 주요 기능 (Key Features)

### 1. AI 커스텀 보이스 알람
*   **ElevenLabs 연동**: 직접 녹음한 음성 파일을 등록하여 세상에 하나뿐인 나만의 AI 목소리로 알람을 설정할 수 있습니다. (3초 이상 음성 필수)
*   **Google Cloud TTS**: 다양한 국적과 성별의 고품질 구글 보이스를 기본 제공합니다.
*   **실시간 시간 안내**: 알람이 울리는 도중에도 1분마다 현재 시각을 감지하여 "현재 시간은 오전 X시 X분입니다"라고 정확하게 읽어줍니다.
*   **미리듣기 실행바**: 보이스 복제 중 녹음본이나 업로드된 파일을 재생할 때 실시간 진행 상태를 시각적으로 확인할 수 있습니다.

### 2. 세련되고 반응적인 사용자 경험 (UX)
*   **반응형 UI**: 다양한 화면 크기에 맞춰 레이아웃과 폰트 크기가 자동으로 최적화되어 어떤 기기에서도 일관된 디자인을 유지합니다.
*   **표시 모드 토글**: 상세 정보와 함께 보는 **상세 모드**와, 핵심 정보만 컴팩트하게 보여주는 **간단 모드**를 상단 버튼으로 자유롭게 전환할 수 있습니다.
*   **지능형 키보드/터치 제어**: 텍스트 입력 중 화면 어디를 터치해도 키보드가 시원하게 내려가며, 배경 터치 시 스마트하게 다이얼로그를 닫거나 키보드만 숨기는 프리미엄 UX를 제공합니다.
*   **다중 삭제 시스템**: 알람 항목을 길게 눌러 여러 개를 선택한 후 한꺼번에 삭제할 수 있으며, 전체 선택 및 삭제 확인 팝업으로 안전하게 관리할 수 있습니다.

### 3. 고도화된 시스템 로직
*   **자동 재예약(Reschedule)**: 반복 요일 설정 시, 알람 종료 후 자동으로 다음 알람을 계산하여 시스템에 등록합니다.
*   **배터리 최적화 대응**: 정확한 시간에 알람이 울릴 수 있도록 전력 관리 예외 처리가 되어 있습니다.
*   **페이드 인(Fade-in) 효과**: 소리가 아주 작게 시작하여 서서히 커짐으로써 기상 시 스트레스를 최소화합니다.

---

## 🛠 기술 스택 (Tech Stack)

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Networking**: Retrofit2, OkHttp3
*   **Async Processing**: Coroutines, Flow
*   **Local Storage**: SharedPreferences, GSON
*   **Media**: Android MediaRecorder, MediaPlayer, TextToSpeech
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
│   ├── main       # 메인 알람 리스트 및 항목 디자인 (표시 모드 전환)
│   ├── edit       # 알람 추가/수정 및 보이스 등록 (반응형 다이얼로그)
│   └── theme      # 디자인 시스템 (Color, Type, Theme)
└── util           # 파일 저장 및 공통 유틸리티 (FileUtil 등)
```

---

## 🚀 시작하기 (Getting Started)

이 프로젝트를 실행하려면 `local.properties` 파일에 다음과 같은 API 키 설정이 필요합니다.

```properties
GOOGLE_API_KEY=YOUR_GOOGLE_CLOUD_TTS_API_KEY
ELEVEN_LABS_API_KEY=YOUR_ELEVENLABS_API_KEY
```

---

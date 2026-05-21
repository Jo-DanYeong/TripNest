# TripNest

TripNest는 사용자가 지도에서 여행 위치를 선택하면 주변 숙소, 관광지, 음식점을 찾아주고 AI로 여행 정보를 요약해 주는 Android 여행 추천 앱입니다.

## 주요 기능

- 여행지 검색 기반 추천 장소 제공
- 지도에서 위치 선택 후 주변 숙소, 관광지, 음식점 조회
- Groq AI API를 이용한 한국어 여행 요약
- 카카오 Local REST API 기반 실제 장소 검색
- 실물 Android 기기 테스트를 위한 `adb reverse` 자동 연결

## 프로젝트 구조

```text
TripNest/
├─ app/                 # Android 앱(Java/XML)
├─ backend/             # Node.js 백엔드 API 서버
├─ gradle/              # Gradle wrapper files
├─ local.properties     # Android 로컬 설정, Git 제외 권장
└─ README.md            # 프로젝트 통합 문서
```

## 기술 스택

- Android: Java, XML, AndroidX Navigation, Material Components, osmdroid
- Backend: Node.js ESM, 기본 `http` 서버
- AI: Groq Chat Completions API
- Map/Places: Kakao Native App Key, Kakao REST API Key

## 필요한 키

카카오 개발자 콘솔에서는 용도별로 키를 나눠 사용합니다.

- Android 앱 지도/SDK: Native App Key
- 백엔드 장소 검색: REST API Key
- 웹 지도: JavaScript Key

이 프로젝트에서는 Android 앱에 Native App Key를 넣고, 백엔드 `.env`에 REST API Key를 넣습니다.

## 환경 설정

### Android `local.properties`

루트의 `local.properties`에 다음 값을 설정합니다.

```properties
BACKEND_BASE_URL=http://127.0.0.1:8080
BACKEND_FALLBACK_URL=http://127.0.0.1:8080
KAKAO_NATIVE_APP_KEY=카카오_네이티브_앱_키
```

실물폰에서 USB 디버깅으로 테스트할 때는 `127.0.0.1:8080`을 사용하고, 백엔드가 자동으로 `adb reverse tcp:8080 tcp:8080`을 실행합니다.

### Backend `.env`

`backend/.env.example`을 복사해 `backend/.env`를 만들고 값을 채웁니다.

```env
PORT=8080
GROQ_API_KEY=Groq_API_Key
GROQ_MODEL=llama-3.3-70b-versatile
KAKAO_REST_API_KEY=카카오_REST_API_키
```

선택값:

```env
ADB_PATH=C:\Users\사용자명\AppData\Local\Android\Sdk\platform-tools\adb.exe
ENABLE_ADB_REVERSE=true
```

`ADB_PATH`를 지정하지 않아도 일반적인 Android Studio 설치 경로와 PATH의 `adb`를 자동으로 찾습니다.

## 백엔드 실행

```powershell
cd backend
npm run dev
```

서버가 정상 실행되면 다음과 비슷한 로그가 나옵니다.

```text
[adb-reverse] tcp:8080 -> tcp:8080 ready
TripNest backend listening on http://0.0.0.0:8080
```

상태 확인:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

카카오 API 확인:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/debug/kakao
```

## Android 앱 실행

Android Studio에서 프로젝트 루트 `TripNest`를 열고 `app` 모듈을 실행합니다.

CLI 빌드:

```powershell
.\gradlew.bat assembleDebug
```

생성 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

실물폰 테스트 시 체크할 것:

- USB 디버깅 허용
- 백엔드 서버 실행 중
- `local.properties`의 `BACKEND_BASE_URL`이 `http://127.0.0.1:8080`
- 앱이 최신 APK로 다시 설치되어 있음

## API

### `GET /api/health`

백엔드 상태와 키 설정 여부를 확인합니다.

### `GET /api/debug/kakao`

카카오 Local API 연결 상태를 확인합니다.

### `POST /api/trips/recommendations`

여행지 검색어를 기준으로 AI 요약과 추천 장소를 반환합니다.

요청 예시:

```json
{
  "destination": "서울",
  "durationDays": 3,
  "styles": ["자연", "맛집", "코스"]
}
```

### `POST /api/maps/nearby`

선택한 좌표 주변의 숙소, 관광지, 음식점을 반환합니다.

요청 예시:

```json
{
  "latitude": 37.5665,
  "longitude": 126.978,
  "radiusMeters": 2000
}
```

응답에는 `stays`, `attractions`, `restaurants` 배열이 포함됩니다.

## 배포

백엔드는 Docker 기반 배포를 지원합니다.

```powershell
docker build -t tripnest-backend ./backend
docker run -p 8080:8080 --env-file ./backend/.env tripnest-backend
```

Render, Railway, Fly.io 같은 컨테이너 호스팅에 배포할 수 있으며, 배포 후 Android `BACKEND_BASE_URL`을 실제 HTTPS API 도메인으로 바꾸면 됩니다.

예시:

```properties
BACKEND_BASE_URL=https://api.tripnest.kr
BACKEND_FALLBACK_URL=https://api.tripnest.kr
```

## 주의 사항

- `.env`와 `local.properties`에는 API 키가 들어가므로 공개 저장소에 올리지 마세요.
- 실물폰에서 `127.0.0.1`은 원래 폰 자신을 의미하지만, `adb reverse`를 사용하면 폰의 `127.0.0.1:8080` 요청이 PC 백엔드로 전달됩니다.
- `adb reverse`는 USB 연결이 끊기거나 기기를 재연결하면 풀릴 수 있습니다. 이 프로젝트의 백엔드는 시작 시 자동으로 다시 연결을 시도합니다.

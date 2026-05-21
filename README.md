# TripNest

TripNest는 여행지를 검색하면 AI 요약, 추천 장소, 주변 숙소/관광지/음식점을 함께 보여주는 Android 여행 추천 앱입니다. 광고성 글은 최대한 걸러내고, 장소별 관련 글과 출처를 따로 확인할 수 있도록 구성했습니다.

## 프로젝트 구조

```text
TripNest/
├─ app/        Android 앱(Java/XML)
├─ backend/    Node.js API 서버
└─ gradle/     Gradle wrapper
```

## 주요 기능

- 이메일/비밀번호 로그인 화면
- 여행지 검색 기반 추천 장소 조회
- 지도에서 위치를 선택한 뒤 주변 숙소/관광지/음식점 조회
- 추천 장소를 관광/음식/숙소 탭으로 분리
- 장소 선택 시 상세 창에서 AI 요약, 관련 글, 출처 확인
- Groq AI 요약
- Kakao Local API 장소 검색

## Android 설정

루트 디렉터리의 `local.properties`에 앱에서 사용할 서버 주소와 Kakao Native App Key를 넣습니다.

로컬 서버를 사용할 때:

```properties
BACKEND_BASE_URL=http://127.0.0.1:8080
BACKEND_FALLBACK_URL=http://127.0.0.1:8080
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

외부 서버(Render 등)를 사용할 때:

```properties
BACKEND_BASE_URL=https://your-tripnest-backend.onrender.com
BACKEND_FALLBACK_URL=https://your-tripnest-backend.onrender.com
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

실물폰에서 로컬 서버를 테스트할 경우 서버 시작 시 `adb reverse tcp:8080 tcp:8080`을 자동으로 시도합니다. 외부 서버를 쓰면 `adb reverse`는 필요하지 않습니다.

## Backend 로컬 실행

`backend/.env` 파일을 만들고 아래 값을 채웁니다.

```env
PORT=8080
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
KAKAO_REST_API_KEY=your_kakao_rest_api_key
```

선택 값:

```env
ADB_PATH=C:\Users\username\AppData\Local\Android\Sdk\platform-tools\adb.exe
ENABLE_ADB_REVERSE=true
```

실행:

```powershell
cd backend
npm run dev
```

상태 확인:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

## Render 배포

이 저장소는 루트의 `render.yaml`과 `backend/Dockerfile`을 포함하고 있어 Render Web Service로 바로 배포할 수 있습니다.

1. GitHub에 프로젝트를 올립니다.
2. Render에서 `New` → `Blueprint` 또는 `Web Service`를 선택합니다.
3. 저장소를 연결하면 루트의 `render.yaml` 기준으로 백엔드가 배포됩니다.
4. Render 환경변수에 아래 값을 등록합니다.

```env
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
KAKAO_REST_API_KEY=your_kakao_rest_api_key
```

배포 후 Render 주소를 앱 `local.properties`의 `BACKEND_BASE_URL`, `BACKEND_FALLBACK_URL`에 넣고 앱을 다시 빌드합니다.

## API

- `GET /api/health`: 백엔드 상태 확인
- `GET /api/debug/kakao`: Kakao API 키/연결 확인
- `POST /api/auth/register`: 회원가입
- `POST /api/auth/login`: 로그인
- `POST /api/trips/recommendations`: 여행지 추천과 AI 요약
- `POST /api/places/insights`: 장소별 관련 글 요약과 출처
- `POST /api/maps/nearby`: 선택 좌표 주변 숙소/관광지/음식점 조회

## Android 빌드

Android Studio에서 루트 폴더를 열고 `app` 모듈을 실행합니다.

CLI 빌드:

```powershell
.\gradlew.bat assembleDebug
```

## 주의

- `backend/.env`, `local.properties`는 공개 저장소에 올리지 않습니다.
- Render 무료 플랜은 서버가 잠들 수 있어 첫 요청이 느릴 수 있습니다.
- 실제 운영에서는 로그인/사용자 데이터 저장소를 별도 데이터베이스로 분리하는 것을 권장합니다.

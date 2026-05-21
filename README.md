# TripNest

TripNest는 여행지를 검색하면 AI 요약과 추천 장소를 보여주고, 지도에서 선택한 위치 주변의 숙소, 관광지, 음식점을 확인할 수 있는 Android 여행 추천 앱입니다.

## 구성

```text
TripNest/
├─ app/        Android 앱(Java/XML)
├─ backend/    Node.js API 서버
└─ gradle/     Gradle wrapper
```

## 주요 기능

- 이메일/비밀번호 회원가입 및 로그인
- 로그인 세션 저장과 마이페이지 로그아웃
- 여행지 검색 기반 추천 장소 조회
- 지도 위치 선택 후 주변 장소 조회
- Groq AI 요약, Kakao Local API 장소 검색

## Android 설정

루트의 `local.properties`에 백엔드 주소와 Kakao Native App Key를 설정합니다.

```properties
BACKEND_BASE_URL=http://127.0.0.1:8080
BACKEND_FALLBACK_URL=http://127.0.0.1:8080
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

실기기에서 USB 디버깅으로 테스트하면 백엔드가 시작될 때 `adb reverse tcp:8080 tcp:8080`을 자동으로 시도합니다.

## Backend 설정

`backend/.env`를 만들고 필요한 값을 채웁니다.

```env
PORT=8080
AUTH_SECRET=change_this_to_a_long_random_secret
AUTH_TOKEN_TTL_SECONDS=604800
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
KAKAO_REST_API_KEY=your_kakao_rest_api_key
```

선택 값:

```env
ADB_PATH=C:\Users\username\AppData\Local\Android\Sdk\platform-tools\adb.exe
ENABLE_ADB_REVERSE=true
```

회원 정보는 개발용으로 `backend/data/users.json`에 저장됩니다. 이 폴더는 Git에서 제외되어 있습니다. 실제 서비스 배포에서는 데이터베이스로 교체하는 것을 권장합니다.

## 실행

백엔드:

```powershell
cd backend
npm run dev
```

상태 확인:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Android 앱은 Android Studio에서 루트 폴더를 열고 `app` 모듈을 실행합니다.

CLI 빌드:

```powershell
.\gradlew.bat assembleDebug
```

## 인증 API

### `POST /api/auth/register`

```json
{
  "name": "홍길동",
  "email": "hong@example.com",
  "password": "password123"
}
```

### `POST /api/auth/login`

```json
{
  "email": "hong@example.com",
  "password": "password123"
}
```

두 API 모두 성공하면 다음 형식으로 응답합니다.

```json
{
  "token": "jwt_token",
  "user": {
    "id": "user_id",
    "email": "hong@example.com",
    "name": "홍길동",
    "createdAt": "2026-05-21T00:00:00.000Z"
  }
}
```

### `GET /api/auth/me`

```http
Authorization: Bearer jwt_token
```

## 여행 API

- `GET /api/health`: 백엔드 상태 확인
- `GET /api/debug/kakao`: Kakao Local API 연결 확인
- `POST /api/trips/recommendations`: 여행지 추천과 AI 요약
- `POST /api/maps/nearby`: 선택 좌표 주변 장소 조회

## 주의

- `backend/.env`, `local.properties`, `backend/data/`는 공개 저장소에 올리지 않습니다.
- `AUTH_SECRET`은 배포 환경에서 반드시 길고 예측하기 어려운 값으로 변경하세요.
- 현재 인증 저장소는 로컬 개발용 JSON 파일입니다. 운영 환경에서는 PostgreSQL, MySQL, MongoDB 같은 영속 데이터베이스를 사용하세요.

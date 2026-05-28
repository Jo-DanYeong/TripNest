[앱 다운로드](https://github.com/Jo-DanYeong/TripNest/releases/download/beta/app-release.apk)
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

## 노트북 서버를 외부에서 접속하기

배포 서버를 따로 쓰지 않고, 내 노트북에서 실행 중인 백엔드를 외부 HTTPS 주소로 열고 싶다면 Cloudflare Tunnel을 사용할 수 있습니다.

1. Cloudflare Tunnel CLI를 설치합니다.

```powershell
winget install --id Cloudflare.cloudflared
```

2. 백엔드를 실행합니다.

```powershell
npm run dev
```

3. 새 터미널에서 터널을 실행합니다.

```powershell
npm run tunnel
```

4. 터미널에 표시되는 주소를 확인합니다.

예시:

```text
https://example-name.trycloudflare.com
```

5. Android `local.properties`를 터널 주소로 바꿉니다.

```properties
BACKEND_BASE_URL=https://example-name.trycloudflare.com
BACKEND_FALLBACK_URL=https://example-name.trycloudflare.com
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

이 방식은 노트북과 터널 터미널이 켜져 있는 동안만 동작합니다. 주소는 실행할 때마다 바뀔 수 있습니다.

백엔드 실행, 터널 주소 반영, 앱 빌드를 한 번에 처리하려면 아래 명령을 사용합니다.

```powershell
npm run online-debug
```

JS 서버를 켤 때 Cloudflare Tunnel도 같이 켜고 싶다면 아래 명령을 사용합니다.

```powershell
npm run dev:online
```

이 명령은 `PORT=8082`, `ENABLE_CLOUDFLARE_TUNNEL=true`로 백엔드를 실행하고, 터널 주소가 나오면 `local.properties`의 `BACKEND_BASE_URL`, `BACKEND_FALLBACK_URL`을 자동으로 갱신합니다. 주소가 바뀐 뒤 앱에 반영하려면 APK를 다시 빌드/설치해야 합니다.

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

## Glitch 배포

Render에서 카드 등록이 부담된다면 Glitch로 테스트 서버를 띄울 수 있습니다. Glitch는 루트의 `package.json`을 실행하므로, 현재 설정에서는 자동으로 `backend` 서버가 시작됩니다.

1. Glitch에서 `New Project`를 만듭니다.
2. `Import from GitHub`로 `TripNest` 저장소를 가져옵니다.
3. Glitch의 `.env` 또는 Secrets에 아래 값을 넣습니다.

```env
PORT=8080
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
KAKAO_REST_API_KEY=your_kakao_rest_api_key
```

4. Glitch가 제공하는 앱 주소를 확인합니다.

예시:

```text
https://your-tripnest-project.glitch.me
```

5. Android `local.properties`를 Glitch 주소로 바꿉니다.

```properties
BACKEND_BASE_URL=https://your-tripnest-project.glitch.me
BACKEND_FALLBACK_URL=https://your-tripnest-project.glitch.me
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

Glitch는 테스트용으로 가볍게 쓰기 좋지만, 무료 환경에서는 서버가 잠들거나 응답이 느려질 수 있습니다.

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

# TripNest React Native

Expo 기반 TripNest 모바일 클라이언트입니다. iOS와 Android에서 같은 코드로 실행하며, 기존 `backend` API를 그대로 사용합니다.

## 실행

```powershell
cd mobile
npm install
npm run ios
```

iOS 시뮬레이터에서 로컬 백엔드를 호출하려면:

```env
EXPO_PUBLIC_BACKEND_BASE_URL=http://127.0.0.1:8080
```

실기기에서 테스트할 때는 PC의 LAN IP나 배포된 HTTPS 주소를 넣으세요. Android의 `local.properties`에 넣은 `BACKEND_BASE_URL`과 같은 값을 `mobile/.env`의 `EXPO_PUBLIC_BACKEND_BASE_URL`에 넣으면 됩니다.

```env
EXPO_PUBLIC_BACKEND_BASE_URL=https://your-tripnest-backend.onrender.com
```

## 포함된 화면

- 로그인/회원가입
- 여행 검색 조건 입력
- AI 추천 결과, 장소 카테고리 필터, 장소 상세 요약
- 지도 위치 선택 및 주변 숙소/관광지/음식점 조회
- 최근 여행/예산 요약

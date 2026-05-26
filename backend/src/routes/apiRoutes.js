import { buildAuthResponse } from "../services/authService.js";
import { readJson, sendJson } from "../http/http.js";

// HTTP 라우팅만 담당하고, 실제 추천/검색 로직은 서비스로 넘긴다.
export function createApiRouter({ config, kakaoClient, tripService }) {
  return async function routeRequest(req, res, url) {
    if (req.method === "GET" && url.pathname === "/api/health") {
      // 앱의 서버 연결 테스트와 배포 상태 확인에 쓰는 가장 가벼운 엔드포인트다.
      sendJson(res, 200, {
        ok: true,
        service: "TripNest Backend",
        kakaoRestKeyConfigured: Boolean(config.KAKAO_REST_API_KEY),
        groqConfigured: Boolean(config.GROQ_API_KEY)
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/debug/kakao") {
      // Kakao REST 키가 제대로 동작하는지 서버에서 직접 확인할 때 사용한다.
      const result = await kakaoClient.checkLocalApi();
      sendJson(res, result.ok ? 200 : 502, result);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/auth/register") {
      sendJson(res, 200, buildAuthResponse(await readJson(req), true));
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/auth/login") {
      sendJson(res, 200, buildAuthResponse(await readJson(req), false));
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/trips/recommendations") {
      sendJson(res, 200, await tripService.buildRecommendation(await readJson(req)));
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/places/insights") {
      sendJson(res, 200, await tripService.buildPlaceInsight(await readJson(req)));
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/maps/nearby") {
      sendJson(res, 200, await tripService.findNearbyPlaces(await readJson(req)));
      return;
    }

    sendJson(res, 404, { error: "요청한 API를 찾을 수 없습니다." });
  };
}

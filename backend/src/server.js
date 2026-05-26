import http from "node:http";
import { URL } from "node:url";

import { createAiClient } from "./clients/aiClient.js";
import { createKakaoClient } from "./clients/kakaoClient.js";
import { loadConfig } from "./config/config.js";
import { sendEmpty, sendJson, setCorsHeaders } from "./http/http.js";
import { setupAdbReverse } from "./platform/adbReverse.js";
import { setupCloudflareTunnel } from "./platform/cloudflareTunnel.js";
import { createApiRouter } from "./routes/apiRoutes.js";
import { createTripService } from "./services/tripService.js";

const config = loadConfig();
const port = Number(config.PORT || 8080);
const host = config.HOST || "0.0.0.0";

// 의존성을 먼저 만들고 라우터에 주입하면 테스트와 교체가 쉬워진다.
const kakaoClient = createKakaoClient(config);
const aiClient = createAiClient(config);
const tripService = createTripService({ config, kakaoClient, aiClient });
const routeRequest = createApiRouter({ config, kakaoClient, tripService });

const server = http.createServer(async (req, res) => {
  setCorsHeaders(res);

  // 브라우저/앱의 CORS 사전 요청은 실제 라우트 처리 전에 끝낸다.
  if (req.method === "OPTIONS") {
    sendEmpty(res, 204);
    return;
  }

  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    await routeRequest(req, res, url);
  } catch (error) {
    console.error("[server-error]", error);
    sendJson(res, error.statusCode || 500, {
      error: "서버 처리 중 오류가 발생했습니다.",
      message: error?.message || String(error)
    });
  }
});

server.on("error", (error) => {
  console.error("[listen-error]", error);
});

// 로컬 Android 기기와 외부 테스트 환경에서 접근하기 위한 보조 연결을 준비한다.
setupAdbReverse(config, port);
setupCloudflareTunnel(config, port);

server.listen(port, host, () => {
  console.log(`TripNest backend listening on http://${host}:${port}`);
});

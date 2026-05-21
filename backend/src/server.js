import http from "node:http";
import { URL } from "node:url";

import { createAiClient } from "./clients/aiClient.js";
import { createKakaoClient } from "./clients/kakaoClient.js";
import { loadConfig } from "./config/config.js";
import { sendEmpty, sendJson, setCorsHeaders } from "./http/http.js";
import { setupAdbReverse } from "./platform/adbReverse.js";
import { createApiRouter } from "./routes/apiRoutes.js";
import { createTripService } from "./services/tripService.js";

const config = loadConfig();
const port = Number(config.PORT || 8080);
const host = config.HOST || "0.0.0.0";

const kakaoClient = createKakaoClient(config);
const aiClient = createAiClient(config);
const tripService = createTripService({ config, kakaoClient, aiClient });
const routeRequest = createApiRouter({ config, kakaoClient, tripService });

const server = http.createServer(async (req, res) => {
  setCorsHeaders(res);

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

setupAdbReverse(config, port);

server.listen(port, host, () => {
  console.log(`TripNest backend listening on http://${host}:${port}`);
});

import fs from "node:fs";
import http from "node:http";
import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import { URL } from "node:url";

const config = loadEnv();
const port = Number(config.PORT || 8080);
const host = config.HOST || "0.0.0.0";

const server = http.createServer(async (req, res) => {
  setCorsHeaders(res);

  if (req.method === "OPTIONS") {
    sendEmpty(res, 204);
    return;
  }

  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

    if (req.method === "GET" && url.pathname === "/api/health") {
      sendJson(res, 200, {
        ok: true,
        service: "TripNest Backend",
        kakaoRestKeyConfigured: Boolean(config.KAKAO_REST_API_KEY),
        groqConfigured: Boolean(config.GROQ_API_KEY)
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/debug/kakao") {
      const result = await checkKakaoLocalApi();
      sendJson(res, result.ok ? 200 : 502, result);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/auth/register") {
      const body = await readJson(req);
      const result = await registerUser(body);
      sendJson(res, 201, result);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/auth/login") {
      const body = await readJson(req);
      const result = await loginUser(body);
      sendJson(res, 200, result);
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/auth/me") {
      const user = requireUser(req);
      sendJson(res, 200, { user: publicUser(user) });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/trips/recommendations") {
      const body = await readJson(req);
      const result = await buildRecommendation(body);
      sendJson(res, 200, result);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/maps/nearby") {
      const body = await readJson(req);
      const result = await findNearbyPlaces(body);
      sendJson(res, 200, result);
      return;
    }

    sendJson(res, 404, { error: "요청한 API를 찾을 수 없습니다." });
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

setupAdbReverse(port);

server.listen(port, host, () => {
  console.log(`TripNest backend listening on http://${host}:${port}`);
});

function setupAdbReverse(targetPort) {
  if (String(config.ENABLE_ADB_REVERSE || "true").toLowerCase() === "false") {
    return;
  }

  const adbPath = findAdbPath();
  if (!adbPath) {
    console.warn("[adb-reverse] adb not found. Set ADB_PATH in backend/.env if you need a physical phone connection.");
    return;
  }

  const result = spawnSync(adbPath, ["reverse", `tcp:${targetPort}`, `tcp:${targetPort}`], {
    encoding: "utf8",
    timeout: 5000,
    windowsHide: true
  });

  if (result.status === 0) {
    console.log(`[adb-reverse] tcp:${targetPort} -> tcp:${targetPort} ready`);
    return;
  }

  const message = (result.stderr || result.stdout || result.error?.message || "").trim();
  console.warn(`[adb-reverse] failed: ${message || "check USB debugging connection"}`);
}

function findAdbPath() {
  const candidates = [
    config.ADB_PATH,
    config.LOCALAPPDATA ? `${config.LOCALAPPDATA}\\Android\\Sdk\\platform-tools\\adb.exe` : "",
    config.ANDROID_HOME ? `${config.ANDROID_HOME}\\platform-tools\\adb.exe` : "",
    config.ANDROID_SDK_ROOT ? `${config.ANDROID_SDK_ROOT}\\platform-tools\\adb.exe` : "",
    "adb"
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (candidate === "adb" || fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return "";
}

async function registerUser(body) {
  const email = normalizeText(body.email, "").toLowerCase();
  const name = normalizeText(body.name, "");
  const password = typeof body.password === "string" ? body.password : "";

  validateAuthInput(email, password, name, true);

  const users = readUsers();
  if (users.some((user) => user.email === email)) {
    throw httpError(409, "이미 가입된 이메일입니다.");
  }

  const user = {
    id: crypto.randomUUID(),
    email,
    name,
    passwordHash: hashPassword(password),
    createdAt: new Date().toISOString()
  };
  users.push(user);
  writeUsers(users);

  return issueAuthResponse(user);
}

async function loginUser(body) {
  const email = normalizeText(body.email, "").toLowerCase();
  const password = typeof body.password === "string" ? body.password : "";

  validateAuthInput(email, password, "", false);

  const user = readUsers().find((item) => item.email === email);
  if (!user || !verifyPassword(password, user.passwordHash)) {
    throw httpError(401, "이메일 또는 비밀번호가 올바르지 않습니다.");
  }

  return issueAuthResponse(user);
}

function issueAuthResponse(user) {
  return {
    token: createToken({ sub: user.id, email: user.email }),
    user: publicUser(user)
  };
}

function publicUser(user) {
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    createdAt: user.createdAt
  };
}

function requireUser(req) {
  const authorization = req.headers.authorization || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  const payload = verifyToken(token);
  if (!payload?.sub) {
    throw httpError(401, "로그인이 필요합니다.");
  }

  const user = readUsers().find((item) => item.id === payload.sub);
  if (!user) {
    throw httpError(401, "사용자를 찾을 수 없습니다.");
  }
  return user;
}

function validateAuthInput(email, password, name, requireName) {
  if (!email || !email.includes("@")) {
    throw httpError(400, "올바른 이메일을 입력해 주세요.");
  }
  if (password.length < 8) {
    throw httpError(400, "비밀번호는 8자 이상이어야 합니다.");
  }
  if (requireName && !name) {
    throw httpError(400, "이름을 입력해 주세요.");
  }
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString("base64url");
  const hash = crypto.scryptSync(password, salt, 64).toString("base64url");
  return `scrypt:${salt}:${hash}`;
}

function verifyPassword(password, storedHash) {
  const [, salt, expectedHash] = String(storedHash || "").split(":");
  if (!salt || !expectedHash) {
    return false;
  }

  const actualHash = crypto.scryptSync(password, salt, 64);
  const expectedBuffer = Buffer.from(expectedHash, "base64url");
  return expectedBuffer.length === actualHash.length
    && crypto.timingSafeEqual(expectedBuffer, actualHash);
}

function createToken(payload) {
  const header = { alg: "HS256", typ: "JWT" };
  const expiresAt = Math.floor(Date.now() / 1000) + Number(config.AUTH_TOKEN_TTL_SECONDS || 60 * 60 * 24 * 7);
  const body = { ...payload, exp: expiresAt };
  const unsigned = `${encodeBase64Url(header)}.${encodeBase64Url(body)}`;
  const signature = sign(unsigned);
  return `${unsigned}.${signature}`;
}

function verifyToken(token) {
  const parts = String(token || "").split(".");
  if (parts.length !== 3) {
    return null;
  }

  const [header, payload, signature] = parts;
  const unsigned = `${header}.${payload}`;
  const expectedSignature = sign(unsigned);
  if (!safeStringEquals(signature, expectedSignature)) {
    return null;
  }

  try {
    const decoded = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    if (decoded.exp && decoded.exp < Math.floor(Date.now() / 1000)) {
      return null;
    }
    return decoded;
  } catch {
    return null;
  }
}

function sign(value) {
  return crypto
    .createHmac("sha256", config.AUTH_SECRET || "tripnest-local-dev-secret")
    .update(value)
    .digest("base64url");
}

function safeStringEquals(a, b) {
  const left = Buffer.from(String(a || ""));
  const right = Buffer.from(String(b || ""));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function encodeBase64Url(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function usersFilePath() {
  return new URL("../data/users.json", import.meta.url);
}

function readUsers() {
  const path = usersFilePath();
  if (!fs.existsSync(path)) {
    return [];
  }

  try {
    const data = JSON.parse(fs.readFileSync(path, "utf8"));
    return Array.isArray(data.users) ? data.users : [];
  } catch {
    return [];
  }
}

function writeUsers(users) {
  const path = usersFilePath();
  fs.mkdirSync(new URL("../data/", import.meta.url), { recursive: true });
  fs.writeFileSync(path, `${JSON.stringify({ users }, null, 2)}\n`, "utf8");
}

function httpError(statusCode, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

async function buildRecommendation(body) {
  const destination = normalizeText(body.destination, "");
  const durationDays = clampNumber(body.durationDays, 3, 1, 14);
  const styles = Array.isArray(body.styles) && body.styles.length > 0
    ? body.styles.map(String)
    : ["자연", "맛집", "코스"];

  if (!destination) {
    return {
      destination: "",
      durationDays,
      styles,
      filteredAdCount: 0,
      summary: "여행지를 먼저 검색해 주세요.",
      places: [],
      sources: []
    };
  }

  const rawSources = getSources(destination);
  const trustedSources = rawSources.filter((source) => !source.ad);
  const places = await searchRecommendedPlaces(destination);
  const summary = await createAiSummary({ destination, durationDays, styles, places, trustedSources });

  return {
    destination,
    durationDays,
    styles,
    filteredAdCount: rawSources.length - trustedSources.length,
    summary,
    places,
    sources: trustedSources
  };
}

async function findNearbyPlaces(body) {
  const latitude = Number(body.latitude);
  const longitude = Number(body.longitude);
  const radiusMeters = clampNumber(body.radiusMeters, 2000, 300, 20000);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    throw new Error("위도와 경도가 필요합니다.");
  }

  const [stays, attractions, restaurants] = await Promise.all([
    searchNearby("AD5", "숙소", longitude, latitude, radiusMeters),
    searchNearby("AT4", "관광지", longitude, latitude, radiusMeters),
    searchNearby("FD6", "맛집", longitude, latitude, radiusMeters)
  ]);

  return {
    center: { latitude, longitude },
    radiusMeters,
    provider: "kakao-local",
    kakaoRestKeyConfigured: Boolean(config.KAKAO_REST_API_KEY),
    stays,
    attractions,
    restaurants
  };
}

async function searchRecommendedPlaces(destination) {
  if (!config.KAKAO_REST_API_KEY) {
    return [];
  }

  const keywords = [
    { query: `${destination} 관광지`, category: "관광" },
    { query: `${destination} 맛집`, category: "음식" },
    { query: `${destination} 숙소`, category: "숙소" }
  ];

  const results = [];
  for (const item of keywords) {
    const places = await searchKakaoKeyword(item.query, { size: 3 });
    const place = places.find((candidate) => candidate.name) || places[0];
    if (place) {
      results.push({
        name: place.name,
        category: item.category,
        description: place.address || `${destination} 주변 ${item.category}`,
        address: place.address,
        latitude: place.latitude,
        longitude: place.longitude,
        kakaoPlaceUrl: place.placeUrl
      });
    }
  }
  return dedupePlaces(results).slice(0, 6);
}

async function searchNearby(categoryCode, keyword, longitude, latitude, radiusMeters) {
  const byCategory = await searchKakaoCategory(categoryCode, longitude, latitude, radiusMeters);
  if (byCategory.length > 0) {
    return byCategory;
  }
  return searchKakaoKeyword(keyword, { longitude, latitude, radiusMeters, size: 10 });
}

async function createAiSummary({ destination, durationDays, styles, places, trustedSources }) {
  const placeNames = places.map((place) => place.name).filter(Boolean).join(", ");
  const fallbackSummary =
    `${destination} ${durationDays}일 여행 기준으로 광고성 문구를 제외하고 믿을 만한 정보만 정리했습니다. ` +
    (placeNames
      ? `추천 후보는 ${placeNames}입니다.`
      : "지도에서 위치를 선택하면 주변 숙소, 관광지, 음식점을 더 정확히 찾을 수 있습니다.");

  if (!config.GROQ_API_KEY) {
    return fallbackSummary;
  }

  const prompt = [
    `여행지: ${destination}`,
    `기간: ${durationDays}일`,
    `여행 스타일: ${styles.join(", ")}`,
    `추천 장소: ${placeNames || "아직 없음"}`,
    `신뢰 출처 수: ${trustedSources.length}`,
    "광고성 표현은 제외하고 한국어로 2문장 요약해 주세요."
  ].join("\n");

  try {
    const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${config.GROQ_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: config.GROQ_MODEL || "llama-3.3-70b-versatile",
        messages: [
          { role: "system", content: "당신은 한국어 여행 정보를 간결하게 요약하는 도우미입니다." },
          { role: "user", content: prompt }
        ],
        temperature: 0.4,
        max_completion_tokens: 220
      })
    });

    if (!response.ok) {
      return fallbackSummary;
    }

    const data = await response.json();
    return data.choices?.[0]?.message?.content?.trim() || fallbackSummary;
  } catch (error) {
    console.error("[groq-error]", error?.message || error);
    return fallbackSummary;
  }
}

async function checkKakaoLocalApi() {
  if (!config.KAKAO_REST_API_KEY) {
    return { ok: false, status: 0, message: "KAKAO_REST_API_KEY가 설정되어 있지 않습니다." };
  }

  const places = await searchKakaoKeyword("서울역", { size: 1, includeStatus: true });
  if (places.status && places.status !== 200) {
    return {
      ok: false,
      status: places.status,
      message: places.message || "카카오 Local API 호출에 실패했습니다."
    };
  }

  return {
    ok: places.length > 0,
    status: places.status || 200,
    samplePlaceName: places[0]?.name || ""
  };
}

async function searchKakaoCategory(categoryCode, longitude, latitude, radiusMeters) {
  if (!config.KAKAO_REST_API_KEY) {
    return [];
  }

  const url = new URL("https://dapi.kakao.com/v2/local/search/category.json");
  url.searchParams.set("category_group_code", categoryCode);
  url.searchParams.set("x", String(longitude));
  url.searchParams.set("y", String(latitude));
  url.searchParams.set("radius", String(radiusMeters));
  url.searchParams.set("sort", "distance");
  url.searchParams.set("size", "10");
  return requestKakaoPlaces(url);
}

async function searchKakaoKeyword(query, options = {}) {
  if (!config.KAKAO_REST_API_KEY) {
    return [];
  }

  const url = new URL("https://dapi.kakao.com/v2/local/search/keyword.json");
  url.searchParams.set("query", query);
  url.searchParams.set("size", String(options.size || 10));

  if (Number.isFinite(options.longitude) && Number.isFinite(options.latitude)) {
    url.searchParams.set("x", String(options.longitude));
    url.searchParams.set("y", String(options.latitude));
    url.searchParams.set("radius", String(options.radiusMeters || 2000));
    url.searchParams.set("sort", "distance");
  }

  return requestKakaoPlaces(url, options);
}

async function requestKakaoPlaces(url, options = {}) {
  try {
    const response = await fetch(url, {
      headers: { Authorization: `KakaoAK ${config.KAKAO_REST_API_KEY}` }
    });

    if (!response.ok) {
      const result = [];
      if (options.includeStatus) {
        result.status = response.status;
        result.message = await safeReadText(response);
      }
      return result;
    }

    const data = await response.json();
    const places = (data.documents || []).map(mapKakaoPlace);
    if (options.includeStatus) {
      places.status = response.status;
    }
    return places;
  } catch (error) {
    console.error("[kakao-error]", error?.message || error);
    const result = [];
    if (options.includeStatus) {
      result.status = 0;
      result.message = error?.message || String(error);
    }
    return result;
  }
}

function mapKakaoPlace(place) {
  return {
    id: place.id || "",
    name: place.place_name || "",
    category: place.category_group_name || "",
    address: place.road_address_name || place.address_name || "",
    distanceMeters: Number(place.distance || 0),
    phone: place.phone || "",
    latitude: place.y || "",
    longitude: place.x || "",
    placeUrl: place.place_url || ""
  };
}

function dedupePlaces(places) {
  const seen = new Set();
  return places.filter((place) => {
    const key = `${place.name}|${place.address}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function getSources(destination) {
  return [
    { title: `${destination} 공식 관광 정보`, type: "public", ad: false },
    { title: `${destination} 실제 방문 후기`, type: "review", ad: false },
    { title: `${destination} 숙소 광고형 콘텐츠`, type: "blog", ad: true },
    { title: `${destination} 로컬 맛집 방문 기록`, type: "review", ad: false },
    { title: `${destination} 체험형 광고 콘텐츠`, type: "blog", ad: true }
  ];
}

async function safeReadText(response) {
  try {
    return await response.text();
  } catch {
    return "";
  }
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 1_000_000) {
        reject(new Error("요청 본문이 너무 큽니다."));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!raw.trim()) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error("JSON 형식이 올바르지 않습니다."));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function sendEmpty(res, statusCode) {
  res.writeHead(statusCode);
  res.end();
}

function setCorsHeaders(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
}

function normalizeText(value, fallback) {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

function clampNumber(value, fallback, min, max) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, number));
}

function loadEnv() {
  const env = { ...process.env };
  try {
    const path = new URL("../.env", import.meta.url);
    if (!fs.existsSync(path)) {
      return env;
    }
    const lines = fs.readFileSync(path, "utf8").split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        continue;
      }
      const index = trimmed.indexOf("=");
      if (index === -1) {
        continue;
      }
      const key = trimmed.slice(0, index).trim();
      const value = trimmed.slice(index + 1).trim();
      if (!env[key]) {
        env[key] = value;
      }
    }
  } catch (error) {
    console.error("[env-error]", error?.message || error);
  }
  return env;
}

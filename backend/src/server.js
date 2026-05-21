import fs from "node:fs";
import http from "node:http";
import { URL } from "node:url";

const config = loadEnv();
const port = Number(config.PORT || 8080);
const host = config.HOST || "0.0.0.0";

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  setCorsHeaders(res);

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  try {
    if (req.method === "GET" && url.pathname === "/api/health") {
      sendJson(res, 200, {
        ok: true,
        service: "TripNest Backend",
        kakaoRestKeyConfigured: Boolean(config.KAKAO_REST_API_KEY)
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/debug/kakao") {
      const result = await checkKakaoLocalApi();
      sendJson(res, result.ok ? 200 : 502, result);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/trips/recommendations") {
      const body = await readJson(req);
      sendJson(res, 200, await buildRecommendation(body));
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/maps/nearby") {
      const body = await readJson(req);
      sendJson(res, 200, await findNearbyPlaces(body));
      return;
    }

    sendJson(res, 404, { error: "Not found" });
  } catch (error) {
    console.error(error);
    sendJson(res, 500, { error: "Internal server error", message: error.message });
  }
});

server.listen(port, host, () => {
  console.log(`TripNest backend listening on http://${host}:${port}`);
});

async function buildRecommendation(body) {
  const destination = normalizeText(body.destination, "");
  const durationDays = Math.max(1, Math.min(14, Number(body.durationDays || 3)));
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

  const rawSources = getMockSources(destination);
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
  const radiusMeters = Math.max(300, Math.min(20000, Number(body.radiusMeters || 2000)));

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    throw new Error("latitude and longitude are required.");
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
    const places = await searchKakaoKeyword(item.query, { size: 1 });
    const place = places[0];
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
  return results;
}

async function searchNearby(categoryCode, keyword, longitude, latitude, radiusMeters) {
  const categoryResults = await searchKakaoCategory(categoryCode, longitude, latitude, radiusMeters);
  if (categoryResults.length > 0) {
    return categoryResults;
  }
  return searchKakaoKeyword(keyword, { longitude, latitude, radiusMeters, size: 10 });
}

async function createAiSummary({ destination, durationDays, styles, places, trustedSources }) {
  const placeNames = places.map((place) => place.name).filter(Boolean).join(", ");
  const fallbackSummary =
    `${destination} ${durationDays}일 일정을 기준으로 광고성 문구를 줄이고 정보를 정리했습니다. ` +
    (placeNames ? `추천 후보는 ${placeNames}입니다.` : "카카오 장소 검색 결과를 기다리는 중입니다.");

  if (!config.GROQ_API_KEY) {
    return fallbackSummary;
  }

  const prompt = [
    `여행지: ${destination}`,
    `기간: ${durationDays}일`,
    `스타일: ${styles.join(", ")}`,
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
          { role: "system", content: "너는 한국어 여행 요약을 제공하는 도우미다." },
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
  } catch {
    return fallbackSummary;
  }
}

async function checkKakaoLocalApi() {
  if (!config.KAKAO_REST_API_KEY) {
    return { ok: false, status: 0, message: "KAKAO_REST_API_KEY가 설정되어 있지 않습니다." };
  }

  const places = await searchKakaoKeyword("서울역", { size: 1, includeStatus: true });
  if (places.status && places.status !== 200) {
    return { ok: false, status: places.status, message: places.message || "카카오 Local API 호출 실패" };
  }

  return { ok: places.length > 0, status: 200, samplePlaceName: places[0]?.name || "" };
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
    const result = [];
    if (options.includeStatus) {
      result.status = 0;
      result.message = error.message;
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

function getMockSources(destination) {
  return [
    { title: `${destination} 공식 관광 정보`, type: "public", ad: false },
    { title: `${destination} 실제 방문 후기`, type: "review", ad: false },
    { title: `${destination} 숙소 광고형 콘텐츠`, type: "blog", ad: true },
    { title: `${destination} 로컬 맛집 탐방 기록`, type: "review", ad: false },
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
        reject(new Error("Request body is too large"));
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
        reject(new Error("Invalid JSON body"));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function setCorsHeaders(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
}

function normalizeText(value, fallback) {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
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
  } catch {
    return env;
  }
  return env;
}

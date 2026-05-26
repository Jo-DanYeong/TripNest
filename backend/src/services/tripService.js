import { isAdvertorialArticle } from "../filters/adFilter.js";
import { httpError } from "../http/http.js";
import { clampNumber, estimateDurationDays, normalizeText } from "../utils/utils.js";

export function createTripService({ config, kakaoClient, aiClient }) {
  async function buildRecommendation(body) {
    // 프론트에서 넘어온 조건을 백엔드 기준으로 다시 정리한다.
    const destination = normalizeText(body.destination);
    const startDate = normalizeText(body.startDate);
    const endDate = normalizeText(body.endDate);
    const budgetWon = clampNumber(body.budgetWon, 0, 0, 100000000);
    const durationDays = estimateDurationDays(startDate, endDate, body.durationDays);
    const styles = Array.isArray(body.styles) && body.styles.length > 0
      ? body.styles.map(String)
      : ["자연", "맛집", "동선"];

    if (!destination) {
      return {
        destination: "",
        durationDays,
        startDate,
        endDate,
        budgetWon,
        styles,
        filteredAdCount: 0,
        summary: "여행지를 먼저 검색해 주세요.",
        places: [],
        relatedSummary: "",
        sources: []
      };
    }

    // 장소 검색과 관련 글 검색은 서로 독립이라 동시에 실행한다.
    const [places, rawArticles] = await Promise.all([
      kakaoClient.searchRecommendedPlaces(destination),
      kakaoClient.searchRelatedArticles(destination)
    ]);
    const trustedArticles = rawArticles.filter((article) => !isAdvertorialArticle(article));
    const sources = trustedArticles.slice(0, 5);
    const filteredAdCount = rawArticles.length - trustedArticles.length;

    // AI 요약도 장소 추천 결과와 정제된 출처를 기반으로 만든다.
    const [summary, relatedSummary] = await Promise.all([
      aiClient.createTripSummary({ destination, durationDays, startDate, endDate, budgetWon, styles, places, relatedArticles: sources }),
      aiClient.createArticleSummary(destination, sources)
    ]);

    return {
      destination,
      durationDays,
      startDate,
      endDate,
      budgetWon,
      styles,
      filteredAdCount,
      summary,
      places,
      relatedSummary,
      sources
    };
  }

  async function buildPlaceInsight(body) {
    // 사용자가 고른 장소 하나에 대해 관련 글을 다시 찾아 더 자세한 요약을 만든다.
    const destination = normalizeText(body.destination);
    const placeName = normalizeText(body.placeName);
    const category = normalizeText(body.category);
    if (!placeName) {
      throw httpError(400, "placeName is required.");
    }

    const queryBase = destination ? `${destination} ${placeName}` : placeName;
    const rawArticles = await kakaoClient.searchRelatedArticles(queryBase);
    const trustedArticles = rawArticles.filter((article) => !isAdvertorialArticle(article));
    const sources = trustedArticles.slice(0, 5);
    const filteredAdCount = rawArticles.length - trustedArticles.length;
    const summary = await aiClient.createArticleSummary(`${placeName}${category ? ` (${category})` : ""}`, sources);

    return { placeName, category, filteredAdCount, summary, sources };
  }

  async function findNearbyPlaces(body) {
    // 지도에서 선택한 좌표를 기준으로 Kakao 카테고리 검색을 수행한다.
    const latitude = Number(body.latitude);
    const longitude = Number(body.longitude);
    const radiusMeters = clampNumber(body.radiusMeters, 2000, 300, 20000);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      throw httpError(400, "위도와 경도가 필요합니다.");
    }

    const nearby = await kakaoClient.findNearbyPlaces({ latitude, longitude, radiusMeters });
    return {
      center: { latitude, longitude },
      radiusMeters,
      provider: "kakao-local",
      kakaoRestKeyConfigured: Boolean(config.KAKAO_REST_API_KEY),
      ...nearby
    };
  }

  return {
    buildRecommendation,
    buildPlaceInsight,
    findNearbyPlaces
  };
}

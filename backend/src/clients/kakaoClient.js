import { dedupeBy, stripHtml } from "../utils/utils.js";

export function createKakaoClient(config) {
  async function checkLocalApi() {
    if (!config.KAKAO_REST_API_KEY) {
      return { ok: false, status: 0, message: "KAKAO_REST_API_KEY가 설정되어 있지 않습니다." };
    }

    const places = await searchKeyword("서울역", { size: 1, includeStatus: true });
    return {
      ok: places.length > 0 && (!places.status || places.status === 200),
      status: places.status || 200,
      samplePlaceName: places[0]?.name || "",
      message: places.message || ""
    };
  }

  async function searchRecommendedPlaces(destination) {
    if (!config.KAKAO_REST_API_KEY) {
      return [];
    }

    const groups = [
      { query: `${destination} 관광지`, category: "관광", kakaoCategory: "AT4" },
      { query: `${destination} 맛집`, category: "음식", kakaoCategory: "FD6" },
      { query: `${destination} 호텔 숙소`, category: "숙소", kakaoCategory: "AD5" }
    ];

    const results = [];
    for (const group of groups) {
      const places = (await searchKeyword(group.query, { size: 12 }))
        .filter((place) => isKakaoPlaceInGroup(place, group.kakaoCategory))
        .slice(0, 8);

      for (const place of places) {
        results.push({
          name: place.name,
          category: group.category,
          description: place.address || `${destination} 주변 ${group.category}`,
          address: place.address,
          latitude: place.latitude,
          longitude: place.longitude,
          kakaoPlaceUrl: place.placeUrl
        });
      }
    }

    return dedupeBy(results, (place) => `${place.name}|${place.address}`).slice(0, 18);
  }

  async function findNearbyPlaces({ latitude, longitude, radiusMeters }) {
    const [stays, attractions, restaurants] = await Promise.all([
      searchNearby("AD5", "숙소", longitude, latitude, radiusMeters),
      searchNearby("AT4", "관광지", longitude, latitude, radiusMeters),
      searchNearby("FD6", "맛집", longitude, latitude, radiusMeters)
    ]);

    return { stays, attractions, restaurants };
  }

  async function searchRelatedArticles(destination) {
    if (!config.KAKAO_REST_API_KEY) {
      return [];
    }

    const queries = [
      `${destination} 여행 후기`,
      `${destination} 가볼만한곳`,
      `${destination} 맛집 관광 코스`
    ];
    const results = [];

    for (const query of queries) {
      const [blogs, webs] = await Promise.all([
        searchContent("blog", query, 5),
        searchContent("web", query, 3)
      ]);
      results.push(...blogs, ...webs);
    }

    return dedupeBy(results, (article) => article.url || `${article.title}|${article.source}`)
      .map((article) => ({ ...article, relevanceScore: scoreArticle(destination, article) }))
      .filter((article) => article.relevanceScore >= 2)
      .sort((a, b) => b.relevanceScore - a.relevanceScore)
      .slice(0, 12);
  }

  async function searchNearby(categoryCode, keyword, longitude, latitude, radiusMeters) {
    const byCategory = await searchCategory(categoryCode, longitude, latitude, radiusMeters);
    if (byCategory.length > 0) {
      return byCategory;
    }
    return searchKeyword(keyword, { longitude, latitude, radiusMeters, size: 10 });
  }

  async function searchCategory(categoryCode, longitude, latitude, radiusMeters) {
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
    return requestPlaces(url);
  }

  async function searchKeyword(query, options = {}) {
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

    return requestPlaces(url, options);
  }

  async function searchContent(type, query, size) {
    const url = new URL(`https://dapi.kakao.com/v2/search/${type}`);
    url.searchParams.set("query", query);
    url.searchParams.set("size", String(size));
    url.searchParams.set("sort", type === "blog" ? "recency" : "accuracy");

    try {
      const response = await fetch(url, {
        headers: { Authorization: `KakaoAK ${config.KAKAO_REST_API_KEY}` }
      });
      if (!response.ok) {
        return [];
      }
      const data = await response.json();
      return (data.documents || []).map((item) => mapArticle(item, type, query));
    } catch (error) {
      console.error("[kakao-search-error]", error?.message || error);
      return [];
    }
  }

  async function requestPlaces(url, options = {}) {
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
      const places = (data.documents || []).map(mapPlace);
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

  return {
    checkLocalApi,
    searchRecommendedPlaces,
    findNearbyPlaces,
    searchRelatedArticles
  };
}

function isKakaoPlaceInGroup(place, categoryCode) {
  const kakaoCategory = place.category || "";
  if (categoryCode === "AT4") {
    return kakaoCategory.includes("관광") || kakaoCategory.includes("명소") || kakaoCategory.includes("문화");
  }
  if (categoryCode === "FD6") {
    return kakaoCategory.includes("음식") || kakaoCategory.includes("식당") || kakaoCategory.includes("카페");
  }
  if (categoryCode === "AD5") {
    return kakaoCategory.includes("숙박") || kakaoCategory.includes("호텔") || kakaoCategory.includes("펜션");
  }
  return true;
}

function mapPlace(place) {
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

function mapArticle(item, type, query) {
  return {
    title: stripHtml(item.title || ""),
    summary: stripHtml(item.contents || ""),
    url: item.url || "",
    source: stripHtml(item.blogname || item.cafename || item.name || "Daum 검색"),
    type,
    query,
    datetime: item.datetime || "",
    ad: false
  };
}

function scoreArticle(destination, article) {
  const title = article.title || "";
  const summary = article.summary || "";
  const text = `${title} ${summary}`;
  const travelWords = ["여행", "가볼만한곳", "관광", "코스", "맛집", "숙소", "후기", "일정"];
  const otherRegions = ["제주", "부산", "강화", "대구", "대전", "광주", "울산", "경주", "강릉", "여수", "속초", "전주"];
  let score = 0;

  if (title.includes(destination)) {
    score += 4;
  }
  if (summary.includes(destination)) {
    score += 1;
  }
  if (travelWords.some((word) => text.includes(word))) {
    score += 2;
  }
  if (otherRegions.some((region) => region !== destination && title.includes(region))) {
    score -= 3;
  }
  if (!title.includes(destination) && otherRegions.some((region) => region !== destination && text.includes(region))) {
    score -= 1;
  }

  return score;
}

async function safeReadText(response) {
  try {
    return await response.text();
  } catch {
    return "";
  }
}

import { formatWon } from "../utils/utils.js";

export function createAiClient(config) {
  async function createTripSummary({ destination, durationDays, startDate, endDate, budgetWon, routePlan, styles, places, relatedArticles }) {
    const placeNames = places.map((place) => place.name).filter(Boolean).join(", ");
    const articleTitles = relatedArticles.map((article) => article.title).filter(Boolean).slice(0, 4).join(", ");
    const tripDates = startDate && endDate ? `${startDate}부터 ${endDate}까지` : `${durationDays}일 일정`;
    const budgetText = budgetWon > 0 ? `예산 ${formatWon(budgetWon)}` : "예산 미입력";
    const routeText = routePlan ? `동선 계획: ${routePlan}` : "동선 계획 미입력";
    const fallback =
      `${destination} ${tripDates} 기준으로 ${budgetText}, ${routeText} 조건을 참고해 광고성 글을 제외한 정보만 정리했습니다. ` +
      (placeNames ? `추천 후보는 ${placeNames}입니다.` : "지도에서 위치를 선택하면 주변 장소를 더 정확히 찾을 수 있습니다.");

    const prompt = [
      `여행지: ${destination}`,
      `날짜: ${tripDates}`,
      `예산: ${budgetText}`,
      routeText,
      `여행 취향: ${styles.join(", ")}`,
      `추천 장소: ${placeNames || "아직 없음"}`,
      `관련 글 제목: ${articleTitles || "없음"}`,
      "광고성 표현은 제외하고 한국어로 2문장 요약을 주세요. 예산이 비어 있으면 예산 제한 없이 추천하고, 동선 계획이 있으면 이동 부담과 예상 비용 관점도 반영해 주세요."
    ].join("\n");

    return requestGroqSummary(prompt, fallback);
  }

  async function createArticleSummary(targetName, articles) {
    if (articles.length === 0) {
      return "광고성 글을 제외한 관련 글을 아직 찾지 못했습니다.";
    }

    const fallback = articles
      .slice(0, 3)
      .map((article) => `- ${article.title}: ${article.summary}`)
      .join("\n");

    const prompt = [
      `${targetName} 관련 글을 광고성 문구 없이 여행자가 참고하기 쉽게 요약해 주세요.`,
      "각 글의 핵심만 3줄 이내로 정리하고 과장된 표현은 빼 주세요.",
      ...articles.slice(0, 5).map((article, index) =>
        `${index + 1}. 제목: ${article.title}\n출처: ${article.source}\n내용: ${article.summary}`
      )
    ].join("\n\n");

    return requestGroqSummary(prompt, fallback);
  }

  async function estimateLodgingNightlyCost({ destination, lodgingName, durationDays, startDate, endDate, budgetWon, relatedArticles }) {
    const articleText = buildArticleText(relatedArticles);
    const fallback = estimateLodgingCostFromText(articleText, budgetWon);
    const target = lodgingName ? `${destination} ${lodgingName}` : destination;
    const prompt = [
      `대상 숙소/지역: ${target}`,
      `일정: ${startDate && endDate ? `${startDate} - ${endDate}` : `${durationDays}일`}`,
      `사용자 예산: ${budgetWon > 0 ? formatWon(budgetWon) : "미입력"}`,
      `검색 글 요약:\n${articleText || "검색 글 없음"}`,
      "위 정보만 참고해서 한국 원화 기준 무난한 숙소 1박 비용을 숫자만 답하세요.",
      "비싼 스위트룸이나 최저가 도미토리는 제외하고, 일반 여행자가 고를 만한 중간값으로 추정하세요."
    ].join("\n");

    return requestNumericEstimate(prompt, fallback);
  }

  async function estimateMealCost({ destination, placeName, relatedArticles }) {
    const articleText = buildArticleText(relatedArticles);
    const fallback = estimateMealCostFromText(`${destination} ${placeName} ${articleText}`);
    const prompt = [
      `음식점: ${destination} ${placeName}`,
      `검색 글 요약:\n${articleText || "검색 글 없음"}`,
      "위 음식점에서 일반 여행자가 1명이 한 끼 먹는 비용을 한국 원화 숫자만 답하세요.",
      "숯불구이, 숯불갈비, 고기집,한식집과 같은 음식점1은 1인분 단품 최저가가 아니라 식사에 필요한 현실적인 1인 비용으로 추정하세요.",
      "음료, 주류, 과한 코스 가격은 제외하고 무난한 주문 기준으로 답하세요."
    ].join("\n");

    return requestNumericEstimate(prompt, fallback);
  }

  async function estimateAdmissionCost({ destination, placeName, relatedArticles }) {
    const articleText = buildArticleText(relatedArticles);
    const fallback = fallbackAdmissionCost(`${destination} ${placeName} ${articleText}`);
    const prompt = [
      `장소: ${destination} ${placeName}`,
      `검색 글 요약:\n${articleText || "검색 글 없음"}`,
      "이 장소가 놀이동산, 테마파크, 전망대, 박물관, 아쿠아리움처럼 입장권이 필요한 곳이면 성인/청소년/노인/어린이 1인 입장권 가격을 JSON만 답하세요.",
      "형식: {\"adult\":59000,\"youth\":52000,\"senior\":46000,\"child\":47000}",
      "무료 장소이거나 입장권 정보가 없으면 모두 0으로 답하세요."
    ].join("\n");

    if (!config.GROQ_API_KEY) {
      return fallback;
    }

    const answer = await requestGroqSummary(prompt, JSON.stringify(fallback));
    return parseAdmissionJson(answer, fallback);
  }

  async function requestNumericEstimate(prompt, fallback) {
    if (!config.GROQ_API_KEY) {
      return fallback;
    }

    const answer = await requestGroqSummary(prompt, String(fallback));
    const estimated = parseWon(answer);
    return estimated > 0 ? estimated : fallback;
  }

  async function requestGroqSummary(prompt, fallback) {
    if (!config.GROQ_API_KEY) {
      return fallback;
    }

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
            { role: "system", content: "당신은 광고성 표현을 걸러내고 한국 여행 정보를 간결하게 정리하는 도우미입니다." },
            { role: "user", content: prompt }
          ],
          temperature: 0.25,
          max_completion_tokens: 320
        })
      });

      if (!response.ok) {
        return fallback;
      }

      const data = await response.json();
      return data.choices?.[0]?.message?.content?.trim() || fallback;
    } catch (error) {
      console.error("[groq-error]", error?.message || error);
      return fallback;
    }
  }

  return {
    createTripSummary,
    createArticleSummary,
    estimateLodgingNightlyCost,
    estimateMealCost,
    estimateAdmissionCost
  };
}

function buildArticleText(articles) {
  return (articles || [])
    .slice(0, 5)
    .map((article, index) => `${index + 1}. ${article.title} / ${article.summary}`)
    .join("\n");
}

function estimateLodgingCostFromText(text, budgetWon) {
  const prices = extractWonPrices(text, 30000, 500000);
  if (prices.length > 0) {
    return median(prices);
  }

  if (budgetWon > 0) {
    return Math.max(50000, Math.min(220000, Math.round(Number(budgetWon) * 0.18)));
  }
  return 90000;
}

function estimateMealCostFromText(text) {
  const source = String(text || "");
  const prices = extractWonPrices(source, 7000, 150000)
    .filter((price) => price >= 10000);
  if (prices.length > 0) {
    return Math.max(keywordMealFallback(source), median(prices));
  }
  return keywordMealFallback(source);
}

function keywordMealFallback(text) {
  const source = String(text || "");
  if (/(숯불|갈비|구이|고기|삼겹|한우|소고기|돼지갈비)/.test(source)) {
    return 25000;
  }
  if (/(횟집|회센터|스시|초밥|오마카세)/.test(source)) {
    return 35000;
  }
  if (/(파스타|스테이크|양식|이탈리안)/.test(source)) {
    return 22000;
  }
  if (/(국밥|칼국수|냉면|분식|김밥|라멘|짬뽕|짜장)/.test(source)) {
    return 11000;
  }
  if (/(카페|커피|디저트|베이커리)/.test(source)) {
    return 9000;
  }
  return 16000;
}

function fallbackAdmissionCost(text) {
  const source = String(text || "");
  if (/(롯데월드|에버랜드|놀이|테마파크|월드)/.test(source)) {
    return { adult: 59000, youth: 52000, senior: 46000, child: 47000 };
  }
  if (/(아쿠아|전망대|박물관|미술관)/.test(source)) {
    return { adult: 25000, youth: 20000, senior: 15000, child: 15000 };
  }
  return { adult: 0, youth: 0, senior: 0, child: 0 };
}

function parseAdmissionJson(answer, fallback) {
  try {
    const jsonText = String(answer || "").match(/\{[\s\S]*\}/)?.[0] || "";
    const parsed = JSON.parse(jsonText);
    return {
      adult: normalizeAdmissionValue(parsed.adult, fallback.adult),
      youth: normalizeAdmissionValue(parsed.youth, fallback.youth),
      senior: normalizeAdmissionValue(parsed.senior, fallback.senior),
      child: normalizeAdmissionValue(parsed.child, fallback.child)
    };
  } catch {
    return fallback;
  }
}

function normalizeAdmissionValue(value, fallback) {
  const number = parseWon(value);
  return number > 0 ? number : fallback;
}

function extractWonPrices(text, min, max) {
  const source = String(text || "");
  const prices = [];
  const patterns = [
    /(\d{1,3}(?:,\d{3})+|\d{4,7})\s*원/g,
    /(\d+(?:\.\d+)?)\s*만\s*원/g,
    /(\d+(?:\.\d+)?)\s*만원/g,
    /(\d+(?:\.\d+)?)\s*천\s*원/g,
    /(\d+(?:\.\d+)?)\s*천원/g
  ];

  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(source)) !== null) {
      let value = Number(String(match[1]).replace(/,/g, ""));
      if (pattern.source.includes("만")) {
        value *= 10000;
      } else if (pattern.source.includes("천")) {
        value *= 1000;
      }
      if (Number.isFinite(value) && value >= min && value <= max) {
        prices.push(Math.round(value));
      }
    }
  }
  return prices;
}

function median(values) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)];
}

function parseWon(value) {
  const number = Number(String(value || "").replace(/[^0-9]/g, ""));
  return Number.isFinite(number) ? number : 0;
}

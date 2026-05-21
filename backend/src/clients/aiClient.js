import { formatWon } from "../utils/utils.js";

export function createAiClient(config) {
  async function createTripSummary({ destination, durationDays, startDate, endDate, budgetWon, styles, places, relatedArticles }) {
    const placeNames = places.map((place) => place.name).filter(Boolean).join(", ");
    const articleTitles = relatedArticles.map((article) => article.title).filter(Boolean).slice(0, 4).join(", ");
    const tripDates = startDate && endDate ? `${startDate}부터 ${endDate}까지` : `${durationDays}일 일정`;
    const budgetText = budgetWon > 0 ? `예산 ${formatWon(budgetWon)}` : "예산 미입력";
    const fallback =
      `${destination} ${tripDates} 기준으로 ${budgetText}을 고려해 광고성 글을 제외한 정보만 정리했습니다. ` +
      (placeNames ? `추천 후보는 ${placeNames}입니다.` : "지도에서 위치를 선택하면 주변 장소를 더 정확히 찾을 수 있습니다.");

    const prompt = [
      `여행지: ${destination}`,
      `날짜: ${tripDates}`,
      `예산: ${budgetText}`,
      `여행 스타일: ${styles.join(", ")}`,
      `추천 장소: ${placeNames || "아직 없음"}`,
      `관련 글 제목: ${articleTitles || "없음"}`,
      "광고성 표현은 제외하고 한국어로 2문장 요약해 주세요. 예산이 있으면 너무 비싼 선택을 피한다는 관점도 반영해 주세요."
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
            { role: "system", content: "당신은 광고성 표현을 걸러내고 한국어 여행 정보를 간결하게 정리하는 도우미입니다." },
            { role: "user", content: prompt }
          ],
          temperature: 0.35,
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
    createArticleSummary
  };
}

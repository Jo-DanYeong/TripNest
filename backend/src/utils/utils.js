export function normalizeText(value, fallback = "") {
  // 입력값이 비어 있으면 호출부가 정한 기본값을 사용한다.
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

export function clampNumber(value, fallback, min, max) {
  // 숫자로 해석할 수 없는 값은 안전한 기본값으로 돌리고, 범위도 함께 제한한다.
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, number));
}

export function stripHtml(value) {
  // Kakao 검색 결과에 섞인 HTML 태그와 기본 엔티티를 화면용 텍스트로 정리한다.
  return String(value || "")
    .replace(/<[^>]+>/g, "")
    .replace(/&quot;/g, "\"")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

export function dedupeBy(items, getKey) {
  // 장소/글 목록에서 같은 URL이나 같은 장소가 반복 노출되지 않도록 한다.
  const seen = new Set();
  return items.filter((item) => {
    const key = getKey(item);
    if (!key || seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

export function estimateDurationDays(startDate, endDate, fallback) {
  // 날짜가 유효할 때만 실제 기간을 계산하고, 아니면 기본 여행 기간을 사용한다.
  const fallbackDays = clampNumber(fallback, 3, 1, 30);
  if (!startDate || !endDate) {
    return fallbackDays;
  }

  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return fallbackDays;
  }

  const days = Math.floor((end.getTime() - start.getTime()) / 86400000) + 1;
  return Math.max(1, Math.min(30, days));
}

export function formatWon(value) {
  // AI 프롬프트와 응답 fallback에서 같은 원화 표기를 쓰기 위한 헬퍼다.
  return `${Number(value || 0).toLocaleString("ko-KR")}원`;
}

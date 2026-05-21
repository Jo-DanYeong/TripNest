export function normalizeText(value, fallback = "") {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

export function clampNumber(value, fallback, min, max) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, number));
}

export function stripHtml(value) {
  return String(value || "")
    .replace(/<[^>]+>/g, "")
    .replace(/&quot;/g, "\"")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

export function dedupeBy(items, getKey) {
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
  return `${Number(value || 0).toLocaleString("ko-KR")}원`;
}

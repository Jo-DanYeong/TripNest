// Catches both explicit ads and softer "hidden ad" patterns.
export function isAdvertorialArticle(article) {
  const text = `${article.title} ${article.summary} ${article.source}`.toLowerCase();
  const definiteAdWords = [
    "광고", "홍보", "협찬", "제휴", "체험단", "원고료", "소정의", "파트너스",
    "제공받", "무료제공", "내돈내산 아님", "업체로부터", "대가를 받",
    "지원받", "제공된", "제공받은", "본 포스팅은"
  ];
  if (definiteAdWords.some((word) => text.includes(word.toLowerCase()))) {
    return true;
  }

  const suspiciousWords = [
    "초대받", "방문권", "이용권", "식사권", "숙박권", "체험 후기", "체험하고",
    "체험했습니다", "리뷰 의무", "솔직한 후기입니다", "정말 추천드려요",
    "강력 추천", "예약하기", "예약 링크", "링크를 통해", "쿠폰", "할인코드",
    "프로모션", "이벤트 참여", "문의는", "상담문의", "예약문의", "바로가기",
    "브랜드로부터", "매장에서 제공", "플레이스 제공"
  ];
  const hypeWords = [
    "인생맛집", "꼭 가야하는", "안 가면 후회", "최고의 선택", "완벽한",
    "필수코스", "확실한", "믿고 가는", "역대급"
  ];
  const commercialActionWords = ["예약", "쿠폰", "할인", "문의", "구매", "바로가기"];

  const suspiciousScore = suspiciousWords.reduce((score, word) =>
    text.includes(word.toLowerCase()) ? score + 1 : score, 0);
  const hypeScore = hypeWords.reduce((score, word) =>
    text.includes(word.toLowerCase()) ? score + 1 : score, 0);
  const hasCommercialAction = commercialActionWords.some((word) => text.includes(word.toLowerCase()));

  return suspiciousScore >= 2
    || (suspiciousScore >= 1 && hypeScore >= 1)
    || (hypeScore >= 2 && hasCommercialAction);
}

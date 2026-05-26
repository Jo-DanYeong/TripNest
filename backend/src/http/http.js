export function setCorsHeaders(res) {
  // 앱, 에뮬레이터, 터널 주소가 달라도 개발 중 API 호출이 막히지 않게 열어둔다.
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
}

export function sendJson(res, statusCode, payload) {
  // 모든 API 응답은 UTF-8 JSON으로 통일한다.
  res.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

export function sendEmpty(res, statusCode) {
  // OPTIONS preflight처럼 본문이 필요 없는 응답에 사용한다.
  res.writeHead(statusCode);
  res.end();
}

export function readJson(req) {
  // Node 기본 http 요청 스트림을 JSON 객체로 바꿔 라우트에서 바로 쓰게 한다.
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 1_000_000) {
        reject(httpError(413, "요청 본문이 너무 큽니다."));
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
        reject(httpError(400, "JSON 형식이 올바르지 않습니다."));
      }
    });
    req.on("error", reject);
  });
}

export function httpError(statusCode, message) {
  // throw만으로도 라우터가 HTTP 상태 코드를 알 수 있게 Error에 값을 붙인다.
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

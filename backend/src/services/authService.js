import { httpError } from "../http/http.js";
import { normalizeText } from "../utils/utils.js";

// 개발용 로컬 인증이다. 운영 인증이 준비되면 Firebase/JWT 검증 흐름으로 교체하면 된다.
export function buildAuthResponse(body, isRegister) {
  // 프론트와 같은 검증 기준을 서버에서도 한 번 더 적용한다.
  const email = normalizeText(body.email).toLowerCase();
  const password = normalizeText(body.password);
  const fallbackName = email.includes("@") ? email.split("@")[0] : "여행자";
  const name = normalizeText(body.name, fallbackName);

  if (!email.includes("@")) {
    throw httpError(400, "올바른 이메일을 입력해 주세요.");
  }
  if (password.length < 8) {
    throw httpError(400, "비밀번호는 8자 이상이어야 합니다.");
  }

  // 실제 계정 저장소가 없으므로 이메일 기반의 안정적인 로컬 토큰/ID를 만든다.
  return {
    token: `local-${isRegister ? "register" : "login"}-${Buffer.from(email).toString("base64url")}`,
    user: {
      id: Buffer.from(email).toString("base64url"),
      email,
      name
    }
  };
}

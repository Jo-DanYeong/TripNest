import { httpError } from "../http/http.js";
import { normalizeText } from "../utils/utils.js";

// Temporary local auth for development. Replace with Firebase when production auth is ready.
export function buildAuthResponse(body, isRegister) {
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

  return {
    token: `local-${isRegister ? "register" : "login"}-${Buffer.from(email).toString("base64url")}`,
    user: {
      id: Buffer.from(email).toString("base64url"),
      email,
      name
    }
  };
}

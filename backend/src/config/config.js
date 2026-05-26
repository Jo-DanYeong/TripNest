import fs from "node:fs";

// dotenv 의존성을 추가하지 않고 backend/.env 값을 직접 읽는다.
export function loadConfig() {
  const env = { ...process.env };
  try {
    const path = new URL("../../.env", import.meta.url);
    if (!fs.existsSync(path)) {
      return env;
    }

    const lines = fs.readFileSync(path, "utf8").split(/\r?\n/);
    for (const line of lines) {
      // 빈 줄과 주석은 설정값으로 보지 않는다.
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        continue;
      }

      const index = trimmed.indexOf("=");
      if (index === -1) {
        continue;
      }

      const key = trimmed.slice(0, index).trim();
      const value = trimmed.slice(index + 1).trim();
      // 실제 환경변수가 이미 있으면 .env가 덮어쓰지 않게 둔다.
      if (!env[key]) {
        env[key] = value;
      }
    }
  } catch (error) {
    console.error("[env-error]", error?.message || error);
  }
  return env;
}

import fs from "node:fs";

// Loads backend/.env without adding a dependency such as dotenv.
export function loadConfig() {
  const env = { ...process.env };
  try {
    const path = new URL("../../.env", import.meta.url);
    if (!fs.existsSync(path)) {
      return env;
    }

    const lines = fs.readFileSync(path, "utf8").split(/\r?\n/);
    for (const line of lines) {
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
      if (!env[key]) {
        env[key] = value;
      }
    }
  } catch (error) {
    console.error("[env-error]", error?.message || error);
  }
  return env;
}

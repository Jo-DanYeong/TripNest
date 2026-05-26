import fs from "node:fs";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __filename = fileURLToPath(import.meta.url);
const backendDir = path.resolve(path.dirname(__filename), "..", "..");
const projectDir = path.resolve(backendDir, "..");

// 실제 기기 테스트를 위해 임시 공개 HTTPS 터널을 띄운다.
export function setupCloudflareTunnel(config, targetPort) {
  if (String(config.ENABLE_CLOUDFLARE_TUNNEL || "false").toLowerCase() !== "true") {
    return;
  }

  const cloudflaredPath = findCloudflaredPath(config);
  if (!cloudflaredPath) {
    console.warn("[cloudflare-tunnel] cloudflared not found. Install it or set CLOUDFLARED_PATH.");
    return;
  }

  const tunnelTarget = `http://127.0.0.1:${targetPort}`;
  const child = spawn(cloudflaredPath, ["tunnel", "--url", tunnelTarget], {
    cwd: projectDir,
    windowsHide: true
  });

  child.stdout.on("data", (chunk) => handleTunnelOutput(String(chunk)));
  child.stderr.on("data", (chunk) => handleTunnelOutput(String(chunk)));
  child.on("error", (error) => {
    console.warn(`[cloudflare-tunnel] failed to start: ${error.message}`);
  });
  child.on("exit", (code) => {
    console.warn(`[cloudflare-tunnel] stopped with code ${code}`);
  });

  process.on("exit", () => child.kill());
}

function handleTunnelOutput(output) {
  // cloudflared 로그에서 발급된 URL을 찾아 Android 빌드 설정에 반영한다.
  process.stdout.write(output);
  const match = output.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/i);
  if (!match) {
    return;
  }

  const url = match[0];
  updateLocalProperties(url);
  console.log(`[cloudflare-tunnel] Android BACKEND_BASE_URL updated: ${url}`);
}

function updateLocalProperties(tunnelUrl) {
  // 앱이 다음 빌드부터 터널 주소를 기본 백엔드로 쓰게 local.properties를 갱신한다.
  const localPropertiesPath = path.join(projectDir, "local.properties");
  if (!fs.existsSync(localPropertiesPath)) {
    return;
  }

  const lines = fs.readFileSync(localPropertiesPath, "utf8").split(/\r?\n/);
  const nextLines = lines.map((line) => {
    if (line.startsWith("BACKEND_BASE_URL=")) {
      return `BACKEND_BASE_URL=${tunnelUrl}`;
    }
    if (line.startsWith("BACKEND_FALLBACK_URL=")) {
      return `BACKEND_FALLBACK_URL=${tunnelUrl}`;
    }
    return line;
  });

  fs.writeFileSync(localPropertiesPath, nextLines.join("\n"), "utf8");
}

function findCloudflaredPath(config) {
  // 직접 지정한 경로, winget 설치 경로, PATH 순서로 cloudflared를 찾는다.
  const localAppData = process.env.LOCALAPPDATA || config.LOCALAPPDATA || "";
  const candidates = [
    config.CLOUDFLARED_PATH,
    localAppData
      ? path.join(
          localAppData,
          "Microsoft",
          "WinGet",
          "Packages",
          "Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe",
          "cloudflared.exe"
        )
      : "",
    "cloudflared"
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (candidate === "cloudflared" || fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return "";
}

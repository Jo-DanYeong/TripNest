import fs from "node:fs";
import { spawnSync } from "node:child_process";

// 실제 Android 기기가 PC의 로컬 백엔드에 접근할 수 있도록 adb reverse를 설정한다.
export function setupAdbReverse(config, targetPort) {
  if (String(config.ENABLE_ADB_REVERSE || "true").toLowerCase() === "false") {
    return;
  }

  const adbPath = findAdbPath(config);
  if (!adbPath) {
    console.warn("[adb-reverse] adb not found. Set ADB_PATH in backend/.env if needed.");
    return;
  }

  const result = spawnSync(adbPath, ["reverse", `tcp:${targetPort}`, `tcp:${targetPort}`], {
    encoding: "utf8",
    timeout: 5000,
    windowsHide: true
  });

  if (result.status === 0) {
    console.log(`[adb-reverse] tcp:${targetPort} -> tcp:${targetPort} ready`);
    return;
  }

  const message = (result.stderr || result.stdout || result.error?.message || "").trim();
  console.warn(`[adb-reverse] failed: ${message || "check USB debugging connection"}`);
}

function findAdbPath(config) {
  // .env 지정 경로, Android SDK 기본 위치, PATH 순서로 adb를 찾는다.
  const candidates = [
    config.ADB_PATH,
    config.LOCALAPPDATA ? `${config.LOCALAPPDATA}\\Android\\Sdk\\platform-tools\\adb.exe` : "",
    config.ANDROID_HOME ? `${config.ANDROID_HOME}\\platform-tools\\adb.exe` : "",
    config.ANDROID_SDK_ROOT ? `${config.ANDROID_SDK_ROOT}\\platform-tools\\adb.exe` : "",
    "adb"
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (candidate === "adb" || fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return "";
}

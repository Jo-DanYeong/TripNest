import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

import { httpError } from "../http/http.js";
import { normalizeText } from "../utils/utils.js";

const authDataDir = path.resolve("data");
const usersFile = path.join(authDataDir, "users.json");

export function buildAuthResponse(body, isRegister) {
  const email = normalizeText(body.email).toLowerCase();
  const password = normalizeText(body.password);
  const fallbackName = email.includes("@") ? email.split("@")[0] : "TripNest";
  const name = normalizeText(body.name, fallbackName);

  if (!email.includes("@")) {
    throw httpError(400, "올바른 이메일을 입력해 주세요.");
  }
  if (password.length < 8) {
    throw httpError(400, "비밀번호는 8자 이상이어야 합니다.");
  }

  const users = loadUsers();
  const existingUser = users[email];

  if (isRegister) {
    if (existingUser) {
      throw httpError(409, "이미 가입된 이메일입니다.");
    }
    users[email] = {
      id: stableUserId(email),
      email,
      name,
      passwordHash: hashPassword(password)
    };
    saveUsers(users);
  } else if (!existingUser || existingUser.passwordHash !== hashPassword(password)) {
    throw httpError(401, "이메일 또는 비밀번호가 올바르지 않습니다.");
  }

  const user = users[email];
  return {
    token: `local-${crypto.randomUUID()}`,
    user: {
      id: user.id,
      email: user.email,
      name: user.name
    }
  };
}

function loadUsers() {
  try {
    return JSON.parse(fs.readFileSync(usersFile, "utf8"));
  } catch {
    return {};
  }
}

function saveUsers(users) {
  fs.mkdirSync(authDataDir, { recursive: true });
  fs.writeFileSync(usersFile, JSON.stringify(users, null, 2), "utf8");
}

function stableUserId(email) {
  return crypto.createHash("sha256").update(email).digest("base64url").slice(0, 24);
}

function hashPassword(password) {
  return crypto.createHash("sha256").update(password).digest("base64url");
}

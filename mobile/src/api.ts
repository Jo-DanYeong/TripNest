import Constants from "expo-constants";
import {
  AuthResult,
  NearbyResult,
  Place,
  PlaceInsight,
  TripRecommendation,
  TripSearch
} from "./types";

const configuredBaseUrl =
  process.env.EXPO_PUBLIC_BACKEND_BASE_URL ||
  (Constants.expoConfig?.extra?.backendBaseUrl as string | undefined) ||
  "http://127.0.0.1:8080";

const fallbackUrls = [
  configuredBaseUrl,
  "http://127.0.0.1:8080",
  "http://10.0.2.2:8080"
];

async function requestJson<T>(path: string, body?: unknown, method = body ? "POST" : "GET"): Promise<T> {
  const uniqueUrls = Array.from(new Set(fallbackUrls.filter(Boolean)));
  let lastError: unknown = null;

  for (const baseUrl of uniqueUrls) {
    try {
      const response = await fetch(`${baseUrl}${path}`, {
        method,
        headers: body ? { "Content-Type": "application/json; charset=utf-8" } : undefined,
        body: body ? JSON.stringify(body) : undefined
      });
      const text = await response.text();
      const data = text ? JSON.parse(text) : {};

      if (!response.ok) {
        const message = data?.message || data?.error || `Backend returned ${response.status}`;
        if (response.status < 500) {
          throw new Error(message);
        }
        lastError = new Error(message);
        continue;
      }
      return data as T;
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError instanceof Error ? lastError : new Error("백엔드에 연결할 수 없습니다.");
}

export const api = {
  health: () => requestJson<{ ok: boolean; service: string }>("/api/health", undefined, "GET"),

  register: (name: string, email: string, password: string) =>
    requestJson<AuthResult>("/api/auth/register", { name, email, password }),

  login: (email: string, password: string) =>
    requestJson<AuthResult>("/api/auth/login", { email, password }),

  recommendations: (search: TripSearch) =>
    requestJson<TripRecommendation>("/api/trips/recommendations", {
      destination: search.destination,
      startDate: search.startDate,
      endDate: search.endDate,
      budgetWon: Math.max(0, search.budgetWon),
      durationDays: 3,
      routePlan: search.routePlan,
      adultCount: Math.max(0, search.adultCount),
      youthCount: Math.max(0, search.youthCount),
      seniorCount: Math.max(0, search.seniorCount),
      childCount: Math.max(0, search.childCount),
      styles: ["자연", "맛집", "동선"]
    }),

  placeInsight: (destination: string, place: Place, search?: TripSearch) =>
    requestJson<PlaceInsight>("/api/places/insights", {
      destination,
      placeName: place.name,
      category: place.category,
      startDate: search?.startDate ?? "",
      endDate: search?.endDate ?? "",
      budgetWon: search?.budgetWon ?? 0,
      adultCount: search?.adultCount ?? 1,
      youthCount: search?.youthCount ?? 0,
      seniorCount: search?.seniorCount ?? 0,
      childCount: search?.childCount ?? 0
    }),

  nearby: (latitude: number, longitude: number, radiusMeters = 2000) =>
    requestJson<NearbyResult>("/api/maps/nearby", { latitude, longitude, radiusMeters })
};

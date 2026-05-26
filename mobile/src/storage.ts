import AsyncStorage from "@react-native-async-storage/async-storage";
import { AuthResult, SavedTrip } from "./types";

const AUTH_KEY = "tripnest.auth";
const TRIP_KEY = "tripnest.savedTrip";

export async function loadAuth(): Promise<AuthResult | null> {
  const raw = await AsyncStorage.getItem(AUTH_KEY);
  return raw ? (JSON.parse(raw) as AuthResult) : null;
}

export async function saveAuth(auth: AuthResult) {
  await AsyncStorage.setItem(AUTH_KEY, JSON.stringify(auth));
}

export async function clearAuth() {
  await AsyncStorage.removeItem(AUTH_KEY);
}

export async function loadSavedTrip(): Promise<SavedTrip> {
  const raw = await AsyncStorage.getItem(TRIP_KEY);
  if (raw) {
    return JSON.parse(raw) as SavedTrip;
  }
  return {
    lastQuery: "",
    latitude: null,
    longitude: null,
    routePlan: "",
    totalBudget: 0,
    estimatedRouteBudget: 0,
    travelers: 1
  };
}

export async function saveTrip(patch: Partial<SavedTrip>) {
  const current = await loadSavedTrip();
  await AsyncStorage.setItem(TRIP_KEY, JSON.stringify({ ...current, ...patch }));
}

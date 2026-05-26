export type User = {
  id: string;
  email: string;
  name: string;
};

export type AuthResult = {
  token: string;
  user: User;
};

export type Source = {
  title: string;
  source: string;
  url: string;
  summary: string;
};

export type Place = {
  name: string;
  description: string;
  category: string;
  address: string;
  kakaoPlaceUrl: string;
  latitude: number;
  longitude: number;
};

export type TripRecommendation = {
  summary: string;
  relatedSummary: string;
  filteredAdCount: number;
  lodgingNightlyCostWon: number;
  places: Place[];
  sources: Source[];
};

export type PlaceInsight = {
  placeName: string;
  summary: string;
  filteredAdCount: number;
  sources: Source[];
  lodgingNightlyCostWon?: number;
  mealCostWon?: number;
  admissionAdultWon?: number;
  admissionYouthWon?: number;
  admissionSeniorWon?: number;
  admissionChildWon?: number;
};

export type NearbyResult = {
  stays: Array<{ name: string } | string>;
  attractions: Array<{ name: string } | string>;
  restaurants: Array<{ name: string } | string>;
};

export type TripSearch = {
  destination: string;
  startDate: string;
  endDate: string;
  budgetWon: number;
  routePlan: string;
  adultCount: number;
  youthCount: number;
  seniorCount: number;
  childCount: number;
};

export type SavedTrip = {
  lastQuery: string;
  latitude: number | null;
  longitude: number | null;
  routePlan: string;
  totalBudget: number;
  estimatedRouteBudget: number;
  travelers: number;
};

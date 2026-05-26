import { Ionicons } from "@expo/vector-icons";
import * as Location from "expo-location";
import { StatusBar } from "expo-status-bar";
import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Linking,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import MapView, { Marker, Region } from "react-native-maps";
import { api } from "./src/api";
import { clearAuth, loadAuth, loadSavedTrip, saveAuth, saveTrip } from "./src/storage";
import { AuthResult, NearbyResult, Place, PlaceInsight, SavedTrip, TripRecommendation, TripSearch } from "./src/types";

type Screen = "auth" | "home" | "results" | "map" | "my";
type Category = "attraction" | "food" | "stay";

const defaultSearch: TripSearch = {
  destination: "",
  startDate: today(),
  endDate: today(),
  budgetWon: 0,
  routePlan: "대중교통 중심",
  adultCount: 1,
  youthCount: 0,
  seniorCount: 0,
  childCount: 0
};

export default function App() {
  const [auth, setAuth] = useState<AuthResult | null>(null);
  const [screen, setScreen] = useState<Screen>("auth");
  const [search, setSearch] = useState<TripSearch>(defaultSearch);
  const [recommendation, setRecommendation] = useState<TripRecommendation | null>(null);
  const [selectedPlaces, setSelectedPlaces] = useState<Place[]>([]);
  const [selectedLocation, setSelectedLocation] = useState({ latitude: 37.5665, longitude: 126.978 });
  const [savedTrip, setSavedTrip] = useState<SavedTrip | null>(null);

  useEffect(() => {
    Promise.all([loadAuth(), loadSavedTrip()]).then(([storedAuth, storedTrip]) => {
      setAuth(storedAuth);
      setSavedTrip(storedTrip);
      setScreen(storedAuth ? "home" : "auth");
    });
  }, []);

  const refreshSavedTrip = async () => setSavedTrip(await loadSavedTrip());

  const openResults = async (nextSearch: TripSearch) => {
    setSearch(nextSearch);
    setRecommendation(null);
    setSelectedPlaces([]);
    setScreen("results");
    await saveTrip({
      lastQuery: nextSearch.destination,
      routePlan: nextSearch.routePlan,
      totalBudget: nextSearch.budgetWon,
      travelers: travelerCount(nextSearch)
    });
    refreshSavedTrip();
  };

  const signOut = async () => {
    await clearAuth();
    setAuth(null);
    setScreen("auth");
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar style="dark" />
      {screen === "auth" ? (
        <AuthScreen
          onSignedIn={async (result) => {
            await saveAuth(result);
            setAuth(result);
            setScreen("home");
          }}
        />
      ) : (
        <View style={styles.appShell}>
          {screen === "home" && <HomeScreen initialSearch={search} onSubmit={openResults} />}
          {screen === "results" && (
            <ResultsScreen
              search={search}
              recommendation={recommendation}
              setRecommendation={setRecommendation}
              selectedPlaces={selectedPlaces}
              setSelectedPlaces={setSelectedPlaces}
              selectedLocation={selectedLocation}
              onPickLocation={() => setScreen("map")}
              onSavedTripChanged={refreshSavedTrip}
            />
          )}
          {screen === "map" && (
            <MapScreen
              selectedLocation={selectedLocation}
              setSelectedLocation={async (location) => {
                setSelectedLocation(location);
                await saveTrip({ latitude: location.latitude, longitude: location.longitude });
                refreshSavedTrip();
              }}
            />
          )}
          {screen === "my" && <MyTripScreen auth={auth} savedTrip={savedTrip} onRefresh={refreshSavedTrip} onSignOut={signOut} />}
          <BottomTabs active={screen} onChange={setScreen} />
        </View>
      )}
    </SafeAreaView>
  );
}

function AuthScreen({ onSignedIn }: { onSignedIn: (result: AuthResult) => void }) {
  const [registerMode, setRegisterMode] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (registerMode && !name.trim()) return Alert.alert("이름을 입력해 주세요.");
    if (!email.includes("@")) return Alert.alert("올바른 이메일을 입력해 주세요.");
    if (password.length < 8) return Alert.alert("비밀번호는 8자 이상이어야 합니다.");

    setLoading(true);
    try {
      onSignedIn(registerMode ? await api.register(name.trim(), email.trim(), password) : await api.login(email.trim(), password));
    } catch (error) {
      Alert.alert("로그인 실패", error instanceof Error ? error.message : "요청을 처리하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined} style={styles.authWrap}>
      <Text style={styles.logo}>TripNest</Text>
      <Text style={styles.authTitle}>{registerMode ? "TripNest 시작하기" : "다시 만나서 반가워요"}</Text>
      <Text style={styles.authSubtitle}>AI 추천, 장소 요약, 주변 탐색을 한 번에 이어갑니다.</Text>
      {registerMode && <Field label="이름" value={name} onChangeText={setName} autoCapitalize="words" />}
      <Field label="이메일" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" />
      <Field label="비밀번호" value={password} onChangeText={setPassword} secureTextEntry />
      <Pressable style={[styles.primaryButton, loading && styles.disabled]} onPress={submit} disabled={loading}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.primaryButtonText}>{registerMode ? "회원가입" : "로그인"}</Text>}
      </Pressable>
      <Pressable onPress={() => setRegisterMode((value) => !value)} disabled={loading}>
        <Text style={styles.linkText}>{registerMode ? "로그인으로 돌아가기" : "계정 만들기"}</Text>
      </Pressable>
    </KeyboardAvoidingView>
  );
}

function HomeScreen({ initialSearch, onSubmit }: { initialSearch: TripSearch; onSubmit: (search: TripSearch) => void }) {
  const [form, setForm] = useState<TripSearch>(initialSearch);

  const update = <K extends keyof TripSearch>(key: K, value: TripSearch[K]) => setForm((current) => ({ ...current, [key]: value }));

  const submit = () => {
    if (!form.destination.trim()) return Alert.alert("검색어를 입력해 주세요.");
    if (travelerCount(form) <= 0) return Alert.alert("인원수는 1명 이상이어야 합니다.");
    onSubmit({ ...form, destination: form.destination.trim(), startDate: form.startDate || today(), endDate: form.endDate || form.startDate || today() });
  };

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Header title="TripNest" subtitle="광고를 줄이고 여행 계획을 또렷하게" />
      <Field label="여행지" value={form.destination} onChangeText={(text) => update("destination", text)} placeholder="예: 부산, 제주, 강릉" />
      <View style={styles.row}>
        <Field style={styles.flex} label="출발일" value={form.startDate} onChangeText={(text) => update("startDate", text)} placeholder="YYYY-MM-DD" />
        <Field style={styles.flex} label="도착일" value={form.endDate} onChangeText={(text) => update("endDate", text)} placeholder="YYYY-MM-DD" />
      </View>
      <Field label="예산" value={form.budgetWon ? String(form.budgetWon) : ""} onChangeText={(text) => update("budgetWon", parseMoney(text))} keyboardType="number-pad" placeholder="예: 300000" />
      <Segmented
        label="동선"
        value={form.routePlan}
        options={["대중교통 중심", "도보 위주", "택시", "자가용", "자전거"]}
        onChange={(value) => update("routePlan", value)}
      />
      <Text style={styles.sectionTitle}>인원</Text>
      <View style={styles.counterGrid}>
        <Counter label="성인" value={form.adultCount} onChange={(value) => update("adultCount", value)} />
        <Counter label="청소년" value={form.youthCount} onChange={(value) => update("youthCount", value)} />
        <Counter label="시니어" value={form.seniorCount} onChange={(value) => update("seniorCount", value)} />
        <Counter label="어린이" value={form.childCount} onChange={(value) => update("childCount", value)} />
      </View>
      <Pressable style={styles.primaryButton} onPress={submit}>
        <Ionicons name="search" size={18} color="#fff" />
        <Text style={styles.primaryButtonText}>추천 보기</Text>
      </Pressable>
    </ScrollView>
  );
}

function ResultsScreen({
  search,
  recommendation,
  setRecommendation,
  selectedPlaces,
  setSelectedPlaces,
  selectedLocation,
  onPickLocation,
  onSavedTripChanged
}: {
  search: TripSearch;
  recommendation: TripRecommendation | null;
  setRecommendation: (recommendation: TripRecommendation | null) => void;
  selectedPlaces: Place[];
  setSelectedPlaces: (places: Place[]) => void;
  selectedLocation: { latitude: number; longitude: number };
  onPickLocation: () => void;
  onSavedTripChanged: () => void;
}) {
  const [category, setCategory] = useState<Category>("attraction");
  const [loading, setLoading] = useState(false);
  const [detailPlace, setDetailPlace] = useState<Place | null>(null);
  const [insight, setInsight] = useState<PlaceInsight | null>(null);
  const [nearby, setNearby] = useState<NearbyResult | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api
      .recommendations(search)
      .then((data) => alive && setRecommendation(data))
      .catch((error) => Alert.alert("추천을 불러오지 못했습니다", error instanceof Error ? error.message : "서버 연결을 확인해 주세요."))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [search.destination, search.startDate, search.endDate, search.budgetWon, search.routePlan]);

  const filteredPlaces = useMemo(() => (recommendation?.places ?? []).filter((place) => inCategory(place, category)), [recommendation, category]);
  const routeSummary = useMemo(() => buildRouteSummary(selectedPlaces, selectedLocation, search), [selectedPlaces, selectedLocation, search]);

  useEffect(() => {
    saveTrip({ routePlan: routeSummary.text, estimatedRouteBudget: routeSummary.estimate, travelers: travelerCount(search) }).then(onSavedTripChanged);
  }, [routeSummary.text, routeSummary.estimate]);

  const togglePlace = (place: Place) => {
    const key = placeKey(place);
    setSelectedPlaces(selectedPlaces.some((item) => placeKey(item) === key) ? selectedPlaces.filter((item) => placeKey(item) !== key) : [...selectedPlaces, place]);
  };

  const openDetail = async (place: Place) => {
    setDetailPlace(place);
    setInsight(null);
    try {
      setInsight(await api.placeInsight(search.destination, place, search));
    } catch {
      setInsight({ placeName: place.name, summary: "관련 글을 불러오지 못했습니다.", filteredAdCount: 0, sources: [] });
    }
  };

  const runNearby = async () => {
    setNearby(null);
    try {
      setNearby(await api.nearby(selectedLocation.latitude, selectedLocation.longitude));
    } catch (error) {
      Alert.alert("주변 정보를 불러오지 못했습니다", error instanceof Error ? error.message : "서버 연결을 확인해 주세요.");
    }
  };

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Header title={`${search.destination} 여행 정보`} subtitle={`${search.startDate} - ${search.endDate} · ${search.routePlan}`} />
      <View style={styles.panel}>
        <Text style={styles.panelLabel}>AI 요약</Text>
        {loading ? <ActivityIndicator color="#16785F" /> : <Text style={styles.bodyText}>{recommendation?.summary || "추천 장소를 찾는 중입니다."}</Text>}
        {!!recommendation && <Text style={styles.mutedText}>광고성 문구 {recommendation.filteredAdCount}개를 제외했습니다.</Text>}
      </View>
      <Segmented
        label="장소"
        value={category}
        options={[
          { label: "관광", value: "attraction" },
          { label: "음식", value: "food" },
          { label: "숙소", value: "stay" }
        ]}
        onChange={(value) => setCategory(value as Category)}
      />
      {filteredPlaces.length === 0 && !loading ? <EmptyText text="추천 결과가 없습니다. 다른 여행지를 검색해 보세요." /> : null}
      {filteredPlaces.map((place, index) => {
        const selected = selectedPlaces.some((item) => placeKey(item) === placeKey(place));
        return (
          <Pressable key={placeKey(place)} style={[styles.placeRow, selected && styles.selectedRow]} onPress={() => openDetail(place)}>
            <View style={styles.placeNumber}><Text style={styles.placeNumberText}>{index + 1}</Text></View>
            <View style={styles.flex}>
              <Text style={styles.placeTitle}>{place.name}</Text>
              <Text style={styles.placeDescription} numberOfLines={2}>{place.description || place.address}</Text>
              <Text style={styles.placeCategory}>{place.category || "상세 보기"}</Text>
            </View>
            <Pressable style={[styles.smallButton, selected && styles.smallButtonActive]} onPress={() => togglePlace(place)}>
              <Text style={[styles.smallButtonText, selected && styles.smallButtonTextActive]}>{selected ? "해제" : "담기"}</Text>
            </Pressable>
          </Pressable>
        );
      })}
      <View style={styles.panel}>
        <Text style={styles.panelLabel}>내가 갈 동선</Text>
        <Text style={styles.bodyText}>{routeSummary.text}</Text>
        <Text style={styles.mutedText}>추정 비용: {formatWon(routeSummary.estimate)}원</Text>
      </View>
      <Pressable style={styles.secondaryButton} onPress={onPickLocation}>
        <Ionicons name="map-outline" size={18} color="#0D4F40" />
        <Text style={styles.secondaryButtonText}>지도에서 위치 선택</Text>
      </Pressable>
      <Pressable style={styles.secondaryButton} onPress={runNearby}>
        <Ionicons name="compass-outline" size={18} color="#0D4F40" />
        <Text style={styles.secondaryButtonText}>선택 위치 주변 조회</Text>
      </Pressable>
      {nearby && <NearbyPanel nearby={nearby} />}
      <PlaceModal place={detailPlace} insight={insight} onClose={() => setDetailPlace(null)} />
    </ScrollView>
  );
}

function MapScreen({
  selectedLocation,
  setSelectedLocation
}: {
  selectedLocation: { latitude: number; longitude: number };
  setSelectedLocation: (location: { latitude: number; longitude: number }) => void;
}) {
  const [region, setRegion] = useState<Region>({
    ...selectedLocation,
    latitudeDelta: 0.04,
    longitudeDelta: 0.04
  });

  const useCurrentLocation = async () => {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (permission.status !== "granted") return Alert.alert("위치 권한이 필요합니다.");
    const current = await Location.getCurrentPositionAsync({});
    const next = {
      latitude: current.coords.latitude,
      longitude: current.coords.longitude,
      latitudeDelta: 0.03,
      longitudeDelta: 0.03
    };
    setRegion(next);
    setSelectedLocation({ latitude: next.latitude, longitude: next.longitude });
  };

  return (
    <View style={styles.mapScreen}>
      <MapView style={styles.map} region={region} onRegionChangeComplete={setRegion}>
        <Marker coordinate={{ latitude: region.latitude, longitude: region.longitude }} title="선택 위치" />
      </MapView>
      <View style={styles.mapPanel}>
        <Text style={styles.panelLabel}>선택 좌표</Text>
        <Text style={styles.bodyText}>{region.latitude.toFixed(6)}, {region.longitude.toFixed(6)}</Text>
        <View style={styles.row}>
          <Pressable style={[styles.secondaryButton, styles.flex]} onPress={useCurrentLocation}>
            <Ionicons name="locate-outline" size={18} color="#0D4F40" />
            <Text style={styles.secondaryButtonText}>현재 위치</Text>
          </Pressable>
          <Pressable style={[styles.primaryButton, styles.flex]} onPress={() => setSelectedLocation({ latitude: region.latitude, longitude: region.longitude })}>
            <Text style={styles.primaryButtonText}>선택 완료</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

function MyTripScreen({
  auth,
  savedTrip,
  onRefresh,
  onSignOut
}: {
  auth: AuthResult | null;
  savedTrip: SavedTrip | null;
  onRefresh: () => void;
  onSignOut: () => void;
}) {
  const [budget, setBudget] = useState("");
  const trip = savedTrip;
  const total = parseMoney(budget) || trip?.totalBudget || 0;
  const estimated = trip?.estimatedRouteBudget || 0;
  const left = total > 0 ? Math.max(0, total - estimated) : 0;
  const perPerson = Math.round(estimated / Math.max(1, trip?.travelers || 1));

  useEffect(() => {
    setBudget(trip?.totalBudget ? String(trip.totalBudget) : "");
  }, [trip?.totalBudget]);

  const saveBudget = async () => {
    await saveTrip({ totalBudget: parseMoney(budget) });
    onRefresh();
  };

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Header title="내 여행" subtitle={auth ? `${auth.user.name || auth.user.email} 계정으로 로그인됨` : "여행 기록"} />
      <View style={styles.panel}>
        <Text style={styles.panelLabel}>최근 여행</Text>
        {trip?.lastQuery ? (
          <>
            <Text style={styles.bodyText}>여행지: {trip.lastQuery}</Text>
            {!!trip.routePlan && <Text style={styles.mutedText}>{trip.routePlan}</Text>}
            {trip.latitude && trip.longitude ? <Text style={styles.mutedText}>위치: {trip.latitude.toFixed(6)}, {trip.longitude.toFixed(6)}</Text> : null}
          </>
        ) : (
          <Text style={styles.mutedText}>아직 저장된 여행이 없습니다.</Text>
        )}
      </View>
      <View style={styles.panel}>
        <Text style={styles.panelLabel}>예산 관리</Text>
        <Field label="총예산" value={budget} onChangeText={setBudget} keyboardType="number-pad" placeholder="예: 300000" />
        <Text style={styles.bodyText}>예상/사용: {formatWon(estimated)}원</Text>
        <Text style={styles.bodyText}>남은 예산: {total > 0 ? `${formatWon(left)}원` : "제한 없음"}</Text>
        <Text style={styles.mutedText}>1인 예상 부담: {formatWon(perPerson)}원</Text>
        <Pressable style={styles.primaryButton} onPress={saveBudget}>
          <Text style={styles.primaryButtonText}>예산 저장</Text>
        </Pressable>
      </View>
      <Pressable style={styles.dangerButton} onPress={onSignOut}>
        <Text style={styles.dangerButtonText}>로그아웃</Text>
      </Pressable>
    </ScrollView>
  );
}

function BottomTabs({ active, onChange }: { active: Screen; onChange: (screen: Screen) => void }) {
  const tabs: Array<{ screen: Screen; icon: keyof typeof Ionicons.glyphMap; label: string }> = [
    { screen: "home", icon: "home-outline", label: "홈" },
    { screen: "results", icon: "sparkles-outline", label: "추천" },
    { screen: "map", icon: "map-outline", label: "지도" },
    { screen: "my", icon: "person-outline", label: "내 여행" }
  ];

  return (
    <View style={styles.tabs}>
      {tabs.map((tab) => {
        const selected = active === tab.screen;
        return (
          <Pressable key={tab.screen} style={styles.tab} onPress={() => onChange(tab.screen)}>
            <Ionicons name={tab.icon} size={21} color={selected ? "#16785F" : "#66706C"} />
            <Text style={[styles.tabText, selected && styles.tabTextActive]}>{tab.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function Field(props: React.ComponentProps<typeof TextInput> & { label: string; style?: object }) {
  const { label, style, ...inputProps } = props;
  return (
    <View style={[styles.fieldWrap, style]}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput style={styles.input} placeholderTextColor="#95A09B" {...inputProps} />
    </View>
  );
}

function Segmented({
  label,
  value,
  options,
  onChange
}: {
  label: string;
  value: string;
  options: Array<string | { label: string; value: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <View style={styles.segmentWrap}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.segmentRow}>
        {options.map((option) => {
          const item = typeof option === "string" ? { label: option, value: option } : option;
          const selected = item.value === value;
          return (
            <Pressable key={item.value} style={[styles.segment, selected && styles.segmentActive]} onPress={() => onChange(item.value)}>
              <Text style={[styles.segmentText, selected && styles.segmentTextActive]}>{item.label}</Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

function Counter({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <View style={styles.counter}>
      <Text style={styles.counterLabel}>{label}</Text>
      <View style={styles.counterControls}>
        <Pressable style={styles.iconButton} onPress={() => onChange(Math.max(0, value - 1))}><Ionicons name="remove" size={18} color="#0D4F40" /></Pressable>
        <Text style={styles.counterValue}>{value}</Text>
        <Pressable style={styles.iconButton} onPress={() => onChange(Math.min(10, value + 1))}><Ionicons name="add" size={18} color="#0D4F40" /></Pressable>
      </View>
    </View>
  );
}

function Header({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <View style={styles.header}>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>
    </View>
  );
}

function EmptyText({ text }: { text: string }) {
  return <Text style={styles.emptyText}>{text}</Text>;
}

function NearbyPanel({ nearby }: { nearby: NearbyResult }) {
  return (
    <View style={styles.panel}>
      <Text style={styles.panelLabel}>주변 정보</Text>
      <Text style={styles.bodyText}>숙소: {joinNearby(nearby.stays)}</Text>
      <Text style={styles.bodyText}>관광: {joinNearby(nearby.attractions)}</Text>
      <Text style={styles.bodyText}>음식: {joinNearby(nearby.restaurants)}</Text>
    </View>
  );
}

function PlaceModal({ place, insight, onClose }: { place: Place | null; insight: PlaceInsight | null; onClose: () => void }) {
  return (
    <Modal visible={Boolean(place)} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <SafeAreaView style={styles.modal}>
        {place && (
          <ScrollView contentContainerStyle={styles.content}>
            <View style={styles.rowBetween}>
              <Text style={styles.modalTitle}>{place.name}</Text>
              <Pressable style={styles.iconButton} onPress={onClose}><Ionicons name="close" size={20} color="#0D4F40" /></Pressable>
            </View>
            <Text style={styles.mutedText}>{place.description || place.address}</Text>
            <View style={styles.panel}>
              <Text style={styles.panelLabel}>관련 글 요약</Text>
              {insight ? <Text style={styles.bodyText}>{insight.summary || "관련 글을 찾지 못했습니다."}</Text> : <ActivityIndicator color="#16785F" />}
            </View>
            {insight?.sources?.map((source) => (
              <Pressable key={`${source.title}${source.url}`} style={styles.sourceCard} onPress={() => source.url && Linking.openURL(source.url)}>
                <Text style={styles.placeTitle}>{source.title}</Text>
                <Text style={styles.placeCategory}>{source.source || "출처 없음"}</Text>
                {!!source.summary && <Text style={styles.placeDescription} numberOfLines={2}>{source.summary}</Text>}
              </Pressable>
            ))}
          </ScrollView>
        )}
      </SafeAreaView>
    </Modal>
  );
}

function inCategory(place: Place, category: Category): boolean {
  const text = `${place.category} ${place.name} ${place.description}`.toLowerCase();
  const food = /음식|맛집|식당|카페|restaurant|food/.test(text);
  const stay = /숙소|호텔|게스트|stay|hotel|lodging/.test(text);
  const attraction = /관광|명소|문화|attraction|tour/.test(text);
  if (category === "food") return food;
  if (category === "stay") return stay;
  return attraction || (!food && !stay);
}

function buildRouteSummary(places: Place[], start: { latitude: number; longitude: number }, search: TripSearch) {
  if (places.length === 0) {
    return {
      text: "장소 카드에서 담기를 누르면 선택한 장소 기준으로 동선과 이동 예산을 계산합니다.",
      estimate: estimateRouteBudget(search.routePlan, search.startDate, search.endDate)
    };
  }

  const ordered = [...places].sort((a, b) => distanceKm(start, a) - distanceKm(start, b));
  const names = ordered.map((place, index) => `${index + 1}. ${place.name}`).join("\n");
  const move = estimateRouteBudget(search.routePlan, search.startDate, search.endDate);
  const food = ordered.filter((place) => inCategory(place, "food")).length * 16000 * travelerCount(search);
  const stay = ordered.filter((place) => inCategory(place, "stay")).length * 90000;
  return { text: names, estimate: move + food + stay };
}

function estimateRouteBudget(routePlan: string, startDate: string, endDate: string) {
  const route = routePlan.toLowerCase();
  const days = estimateDurationDays(startDate, endDate);
  let daily = 12000;
  if (route.includes("도보")) daily = 5000;
  else if (route.includes("자가용")) daily = 55000;
  else if (route.includes("택시")) daily = 42000;
  else if (route.includes("자전거")) daily = 9000;
  return daily * days;
}

function estimateDurationDays(startDate: string, endDate: string) {
  const start = new Date(startDate).getTime();
  const end = new Date(endDate).getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 3;
  return Math.max(1, Math.min(30, Math.round((end - start) / 86400000) + 1));
}

function distanceKm(start: { latitude: number; longitude: number }, place: Place) {
  if (!Number.isFinite(place.latitude) || !Number.isFinite(place.longitude)) return Number.MAX_SAFE_INTEGER;
  const toRad = (value: number) => (value * Math.PI) / 180;
  const dLat = toRad(place.latitude - start.latitude);
  const dLon = toRad(place.longitude - start.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(start.latitude)) * Math.cos(toRad(place.latitude)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function placeKey(place: Place) {
  return `${place.name}|${place.address}`;
}

function travelerCount(search: TripSearch) {
  return Math.max(0, search.adultCount + search.youthCount + search.seniorCount + search.childCount);
}

function parseMoney(value: string) {
  return Number(value.replace(/[^0-9]/g, "")) || 0;
}

function formatWon(value: number) {
  return new Intl.NumberFormat("ko-KR").format(Math.max(0, value));
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function joinNearby(items: NearbyResult[keyof NearbyResult]) {
  const names = items.map((item) => (typeof item === "string" ? item : item.name)).filter(Boolean);
  return names.slice(0, 3).join(", ") || "데이터 없음";
}

const colors = {
  primary: "#16785F",
  primaryDark: "#0D4F40",
  primaryLight: "#E7F4EF",
  accent: "#C7781B",
  background: "#F7F8F7",
  card: "#FFFFFF",
  text: "#18211E",
  muted: "#66706C",
  divider: "#E2E8E4",
  danger: "#E24B4A"
};

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  appShell: { flex: 1 },
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: 18, paddingBottom: 112, gap: 14 },
  authWrap: { flex: 1, justifyContent: "center", padding: 24, backgroundColor: colors.background, gap: 14 },
  logo: { color: colors.primary, fontSize: 18, fontWeight: "800" },
  authTitle: { color: colors.text, fontSize: 30, fontWeight: "800" },
  authSubtitle: { color: colors.muted, fontSize: 15, lineHeight: 22, marginBottom: 8 },
  header: { gap: 4, marginBottom: 2 },
  title: { color: colors.text, fontSize: 28, fontWeight: "800" },
  subtitle: { color: colors.muted, fontSize: 14, lineHeight: 20 },
  fieldWrap: { gap: 7 },
  fieldLabel: { color: colors.primaryDark, fontSize: 13, fontWeight: "700" },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.divider,
    backgroundColor: colors.card,
    borderRadius: 8,
    paddingHorizontal: 14,
    color: colors.text,
    fontSize: 15
  },
  row: { flexDirection: "row", gap: 10, alignItems: "center" },
  rowBetween: { flexDirection: "row", gap: 12, alignItems: "center", justifyContent: "space-between" },
  flex: { flex: 1 },
  sectionTitle: { color: colors.text, fontSize: 16, fontWeight: "800", marginTop: 4 },
  primaryButton: {
    minHeight: 50,
    borderRadius: 8,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 8,
    paddingHorizontal: 14
  },
  primaryButtonText: { color: "#fff", fontSize: 15, fontWeight: "800" },
  secondaryButton: {
    minHeight: 48,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.divider,
    backgroundColor: colors.primaryLight,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 8,
    paddingHorizontal: 14
  },
  secondaryButtonText: { color: colors.primaryDark, fontSize: 14, fontWeight: "800" },
  dangerButton: { minHeight: 48, borderRadius: 8, backgroundColor: "#FCEBEB", alignItems: "center", justifyContent: "center" },
  dangerButtonText: { color: colors.danger, fontSize: 14, fontWeight: "800" },
  disabled: { opacity: 0.65 },
  linkText: { color: colors.primaryDark, fontWeight: "800", textAlign: "center", padding: 8 },
  segmentWrap: { gap: 8 },
  segmentRow: { gap: 8 },
  segment: { borderRadius: 8, borderWidth: 1, borderColor: colors.divider, paddingVertical: 10, paddingHorizontal: 13, backgroundColor: colors.card },
  segmentActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  segmentText: { color: colors.primaryDark, fontWeight: "700" },
  segmentTextActive: { color: "#fff" },
  counterGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  counter: { width: "48%", backgroundColor: colors.card, borderRadius: 8, borderWidth: 1, borderColor: colors.divider, padding: 12, gap: 10 },
  counterLabel: { color: colors.text, fontWeight: "700" },
  counterControls: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  counterValue: { color: colors.text, fontSize: 18, fontWeight: "800" },
  iconButton: { width: 36, height: 36, borderRadius: 8, backgroundColor: colors.primaryLight, alignItems: "center", justifyContent: "center" },
  panel: { backgroundColor: colors.card, borderRadius: 8, borderWidth: 1, borderColor: colors.divider, padding: 14, gap: 8 },
  panelLabel: { color: colors.primaryDark, fontSize: 13, fontWeight: "800" },
  bodyText: { color: colors.text, fontSize: 14, lineHeight: 21 },
  mutedText: { color: colors.muted, fontSize: 13, lineHeight: 19 },
  emptyText: { color: colors.muted, textAlign: "center", padding: 18 },
  placeRow: { flexDirection: "row", gap: 10, alignItems: "center", backgroundColor: colors.card, borderRadius: 8, borderWidth: 1, borderColor: colors.divider, padding: 12 },
  selectedRow: { backgroundColor: colors.primaryLight, borderColor: "#BBDDD2" },
  placeNumber: { width: 30, height: 30, borderRadius: 15, backgroundColor: colors.primary, alignItems: "center", justifyContent: "center" },
  placeNumberText: { color: "#fff", fontWeight: "800" },
  placeTitle: { color: colors.text, fontWeight: "800", fontSize: 14 },
  placeDescription: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 4 },
  placeCategory: { color: colors.primaryDark, fontSize: 11, fontWeight: "700", marginTop: 5 },
  smallButton: { minWidth: 54, height: 34, borderRadius: 8, backgroundColor: colors.primaryLight, alignItems: "center", justifyContent: "center" },
  smallButtonActive: { backgroundColor: colors.primary },
  smallButtonText: { color: colors.primaryDark, fontSize: 12, fontWeight: "800" },
  smallButtonTextActive: { color: "#fff" },
  tabs: { position: "absolute", left: 12, right: 12, bottom: 14, minHeight: 64, borderRadius: 8, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.divider, flexDirection: "row", shadowColor: "#000", shadowOpacity: 0.08, shadowRadius: 18, shadowOffset: { width: 0, height: 8 }, elevation: 6 },
  tab: { flex: 1, alignItems: "center", justifyContent: "center", gap: 4 },
  tabText: { color: colors.muted, fontSize: 11, fontWeight: "700" },
  tabTextActive: { color: colors.primary },
  mapScreen: { flex: 1, backgroundColor: colors.background },
  map: { flex: 1 },
  mapPanel: { position: "absolute", left: 14, right: 14, bottom: 92, backgroundColor: colors.card, borderRadius: 8, borderWidth: 1, borderColor: colors.divider, padding: 14, gap: 10 },
  modal: { flex: 1, backgroundColor: colors.background },
  modalTitle: { flex: 1, color: colors.text, fontSize: 24, fontWeight: "800" },
  sourceCard: { backgroundColor: colors.card, borderRadius: 8, borderWidth: 1, borderColor: colors.divider, padding: 13 }
});

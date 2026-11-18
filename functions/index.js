"use strict";

// Firebase 프로젝트의 인증·저장소·비밀값을 사용하는 단일 API 게이트웨이.
const crypto = require("node:crypto");
const express = require("express");
const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

admin.initializeApp();
const db = admin.firestore();
const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "10kb" }));

const TMAP_APP_KEY = defineSecret("TMAP_APP_KEY");
const TMAP_TRANSIT_APP_KEY = defineSecret("TMAP_TRANSIT_APP_KEY");
const KMA_SERVICE_KEY = defineSecret("KMA_SERVICE_KEY");
const SEOUL_SUBWAY_API_KEY = defineSecret("SEOUL_SUBWAY_API_KEY");
const TAGO_BUS_SERVICE_KEY = defineSecret("TAGO_BUS_SERVICE_KEY");

const QUOTAS = Object.freeze({
  perMinute: 20, dailyUnits: 250, transitDaily: 20, walkingDaily: 50,
});
const MAX_RESPONSE_BYTES = 2_000_000;

class SafeError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
function invalid(message = "요청 정보를 확인하세요") {
  throw new SafeError(422, message);
}
function requiredSecret(secret) {
  const value = secret.value();
  if (!value || !value.trim()) {
    throw new SafeError(503, "현재 이용할 수 없는 서비스");
  }
  return value.trim();
}
function decodedKey(secret) {
  const raw = requiredSecret(secret);
  try {
    return decodeURIComponent(raw);
  } catch (_) {
    return raw;
  }
}
function boundedNumber(value, min, max) {
  const result = Number(value);
  if (value === undefined || value === null || value === "" ||
      !Number.isFinite(result) || result < min || result > max) invalid();
  return result;
}
function boundedText(value, maxLength) {
  if (typeof value !== "string") invalid();
  const text = value.trim();
  if (!text || text.length > maxLength) invalid();
  return text;
}
function coordinates(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) invalid();
  return {
    startX: boundedNumber(body.startX, 124, 132),
    startY: boundedNumber(body.startY, 33, 39),
    endX: boundedNumber(body.endX, 124, 132),
    endY: boundedNumber(body.endY, 33, 39),
  };
}
function fixedUrl(base, params) {
  const url = new URL(base);
  for (const [name, value] of Object.entries(params || {})) {
    url.searchParams.set(name, String(value));
  }
  return url.toString();
}
async function relay(url, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  const request = {
    method: options.method || "GET",
    headers,
    redirect: "error",
    signal: AbortSignal.timeout(19000),
  };
  if (options.payload !== undefined) {
    request.headers["Content-Type"] = "application/json; charset=UTF-8";
    request.body = JSON.stringify(options.payload);
  }
  try {
    const response = await fetch(url, request);
    if (response.status !== 200) {
      // 제공기관 응답 본문과 비밀키는 사용자 응답 및 로그에 기록하지 않음.
      throw new SafeError(502, "외부 정보 조회 불가");
    }
    if (!response.body) throw new SafeError(502, "외부 정보 조회 불가");
    const reader = response.body.getReader();
    const blocks = [];
    let length = 0;
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        length += value.byteLength;
        if (length > MAX_RESPONSE_BYTES) {
          throw new SafeError(502, "외부 응답 크기 초과");
        }
        blocks.push(Buffer.from(value));
      }
    } finally {
      reader.releaseLock();
    }
    const result = JSON.parse(Buffer.concat(blocks).toString("utf8"));
    if (!result || typeof result !== "object" || Array.isArray(result)) {
      throw new SafeError(502, "외부 응답 형식 오류");
    }
    return result;
  } catch (error) {
    if (error instanceof SafeError) throw error;
    if (error.name === "TimeoutError" || error.name === "AbortError") {
      throw new SafeError(504, "연결 시간 초과");
    }
    throw new SafeError(502, "외부 정보 조회 불가");
  }
}

// Firestore 트랜잭션으로 인스턴스 간 사용자별 API 사용량 제한.
async function checkQuota(uid, category) {
  const now = new Date();
  const day = now.toISOString().slice(0, 10).replaceAll("-", "");
  const minute = now.toISOString().slice(0, 16);
  const weight = category === "transit" ? 5 : category === "walking" ? 2 : 1;
  const hash = crypto.createHash("sha256").update(uid + ":" + day).digest("hex");
  const ref = db.collection("apiGatewayUsage").doc(hash);
  try {
    const allowed = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(ref);
      const state = snapshot.exists ? snapshot.data() : {};
      const count = state.minute === minute ? (state.minuteCount || 0) : 0;
      const daily = state.dailyUnits || 0;
      const transit = state.transitCount || 0;
      const walking = state.walkingCount || 0;
      if (count >= QUOTAS.perMinute || daily + weight > QUOTAS.dailyUnits ||
          (category === "transit" && transit >= QUOTAS.transitDaily) ||
          (category === "walking" && walking >= QUOTAS.walkingDaily)) {
        return false;
      }
      transaction.set(ref, {
        minute, minuteCount: count + 1, dailyUnits: daily + weight,
        transitCount: transit + (category === "transit" ? 1 : 0),
        walkingCount: walking + (category === "walking" ? 1 : 0),
        expiresAt: admin.firestore.Timestamp.fromMillis(
          now.getTime() + 3 * 24 * 60 * 60 * 1000),
      });
      return true;
    });
    if (!allowed) throw new SafeError(429, "요청 횟수 제한");
  } catch (error) {
    if (error instanceof SafeError) throw error;
    // 저장소 접근 실패 시 제한을 우회하지 않음.
    throw new SafeError(503, "요청 처리 일시 중단");
  }
}

// 이 인증은 Firebase SDK로 로그인한 사용자만 외부 데이터 조회 가능하게 함.
app.use("/v1", async (req, res, next) => {
  const authorization = req.get("Authorization") || "";
  if (!authorization.startsWith("Bearer ") ||
      authorization.length < 20 || authorization.length > 12000) {
    return res.status(401).json({ detail: "로그인 필요" });
  }
  try {
    const token = authorization.slice(7).trim();
    const claims = await admin.auth().verifyIdToken(token);
    if (!claims.uid) return res.status(401).json({ detail: "로그인 필요" });
    req.userId = claims.uid;
    return next();
  } catch (_) {
    return res.status(401).json({ detail: "로그인 유효기간 만료" });
  }
});

function handler(category, fn) {
  return async (req, res, next) => {
    try {
      await checkQuota(req.userId, category);
      const value = await fn(req);
      res.status(200).json(value);
    } catch (error) {
      next(error);
    }
  };
}

app.get("/healthz", (_req, res) => res.json({ status: "ok" }));

app.get("/v1/pois", handler("pois", (req) => {
  const q = boundedText(req.query.q, 70);
  if (q.length < 2) invalid();
  const url = fixedUrl("https://apis.openapi.sk.com/tmap/pois", {
    version: 1, searchKeyword: q, searchType: "all", searchtypCd: "A",
    count: 10, page: 1, resCoordType: "WGS84GEO", reqCoordType: "WGS84GEO",
  });
  return relay(url, { headers: { appKey: requiredSecret(TMAP_APP_KEY) } });
}));

app.post("/v1/transit/routes", handler("transit", (req) => {
  const location = coordinates(req.body);
  if (req.body.count !== undefined && Number(req.body.count) > 3) invalid();
  const payload = {
    startX: String(location.startX), startY: String(location.startY),
    endX: String(location.endX), endY: String(location.endY),
    count: 3, lang: 0, format: "json",
  };
  return relay("https://apis.openapi.sk.com/transit/routes", {
    method: "POST",
    headers: { appKey: requiredSecret(TMAP_TRANSIT_APP_KEY) },
    payload,
  });
}));

app.post("/v1/walking", handler("walking", (req) => {
  const location = coordinates(req.body);
  const payload = {
    ...location,
    startName: req.body.startName === undefined ? "출발" :
      boundedText(req.body.startName, 50),
    endName: req.body.endName === undefined ? "도착" :
      boundedText(req.body.endName, 50),
    reqCoordType: "WGS84GEO", resCoordType: "WGS84GEO",
  };
  return relay("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1", {
    method: "POST", headers: { appKey: requiredSecret(TMAP_APP_KEY) }, payload,
  });
}));

app.get("/v1/weather", handler("weather", (req) => {
  const baseDate = boundedText(req.query.base_date, 8);
  const baseTime = boundedText(req.query.base_time, 4);
  if (!/^\d{8}$/.test(baseDate) || !/^\d{4}$/.test(baseTime)) invalid();
  const nx = boundedNumber(req.query.nx, 1, 149);
  const ny = boundedNumber(req.query.ny, 1, 253);
  const url = fixedUrl(
    "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst", {
      serviceKey: decodedKey(KMA_SERVICE_KEY), pageNo: 1, numOfRows: 1000,
      dataType: "JSON", base_date: baseDate, base_time: baseTime, nx, ny,
    });
  return relay(url);
}));

app.get("/v1/subway", handler("subway", (req) => {
  const station = boundedText(req.query.station, 30);
  if (!/^[가-힣A-Za-z0-9() .·-]+$/.test(station)) invalid();
  const secret = encodeURIComponent(requiredSecret(SEOUL_SUBWAY_API_KEY));
  // 서울시 제공기관이 안내하는 HTTP 주소를 서버에서만 사용.
  const url = "http://swopenAPI.seoul.go.kr/api/subway/" + secret +
      "/json/realtimeStationArrival/0/30/" + encodeURIComponent(station);
  return relay(url);
}));

app.get("/v1/bus/stops", handler("bus", (req) => {
  const lat = boundedNumber(req.query.lat, 33, 39);
  const lon = boundedNumber(req.query.lon, 124, 132);
  const url = fixedUrl(
    "https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList", {
      serviceKey: decodedKey(TAGO_BUS_SERVICE_KEY), _type: "json",
      pageNo: 1, numOfRows: 100, gpsLati: lat, gpsLong: lon,
    });
  return relay(url);
}));

app.get("/v1/bus/arrivals", handler("bus", (req) => {
  const cityCode = boundedText(req.query.cityCode, 10);
  const nodeId = boundedText(req.query.nodeId, 80);
  if (!/^\d{1,10}$/.test(cityCode) || !/^[A-Za-z0-9_-]+$/.test(nodeId))
    invalid();
  const url = fixedUrl(
    "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList", {
      serviceKey: decodedKey(TAGO_BUS_SERVICE_KEY), _type: "json",
      pageNo: 1, numOfRows: 100, cityCode, nodeId,
    });
  return relay(url);
}));

app.use((error, _req, res, _next) => {
  const status = error instanceof SafeError ? error.status :
      error && error.type === "entity.too.large" ? 413 : 500;
  const message = error instanceof SafeError ? error.message :
      status === 413 ? "요청 크기 초과" : "서비스 일시 오류";
  res.status(status).json({ detail: message });
});

exports.commuteApi = onRequest({
  region: "asia-northeast3",
  memory: "256MiB",
  timeoutSeconds: 45,
  maxInstances: 3,
  concurrency: 20,
  minInstances: 0,
  invoker: "public", // 요청은 공개 HTTPS, /v1 인증 검사는 서버에서 강제.
  secrets: [
    TMAP_APP_KEY, TMAP_TRANSIT_APP_KEY, KMA_SERVICE_KEY,
    SEOUL_SUBWAY_API_KEY, TAGO_BUS_SERVICE_KEY,
  ],
}, app);

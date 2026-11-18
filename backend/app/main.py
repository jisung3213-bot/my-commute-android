"""Authenticated, allowlisted provider gateway. Secrets stay on the server."""
import hashlib
import json
import os
import re
from datetime import datetime, timedelta, timezone
from urllib.parse import quote, unquote

import firebase_admin
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from firebase_admin import auth, firestore
from pydantic import BaseModel, ConfigDict, Field
import httpx

PROJECT = os.environ.get("FIREBASE_PROJECT_ID", "").strip()
if not PROJECT:
    raise RuntimeError("FIREBASE_PROJECT_ID must be configured")
firebase_admin.initialize_app(options={"projectId": PROJECT})
db = firestore.client()
app = FastAPI(title="My Commute API", version="1", docs_url=None,
              redoc_url=None, openapi_url=None)
TIMEOUT = httpx.Timeout(connect=6, read=17, write=6, pool=3)
MAX_RESPONSE = 2_000_000
DAILY_UNITS = int(os.getenv("DAILY_UNITS", "250"))
PER_MINUTE = int(os.getenv("PER_MINUTE", "20"))
TRANSIT_PER_DAY = int(os.getenv("TRANSIT_PER_DAY", "20"))
WALKING_PER_DAY = int(os.getenv("WALKING_PER_DAY", "50"))


class Coordinates(BaseModel):
    model_config = ConfigDict(extra="forbid")
    startX: float = Field(ge=124, le=132)
    startY: float = Field(ge=33, le=39)
    endX: float = Field(ge=124, le=132)
    endY: float = Field(ge=33, le=39)


class TransitRequest(Coordinates):
    count: int = Field(default=3, ge=1, le=3)
    lang: int = Field(default=0, ge=0, le=0)
    format: str = Field(default="json", pattern="^json$")


class WalkingRequest(Coordinates):
    startName: str = Field(default="출발", max_length=50)
    endName: str = Field(default="도착", max_length=50)
    reqCoordType: str = Field(default="WGS84GEO", pattern="^WGS84GEO$")
    resCoordType: str = Field(default="WGS84GEO", pattern="^WGS84GEO$")


def require_user(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="로그인 필요")
    token = authorization[7:].strip()
    if not token or len(token) > 10000:
        raise HTTPException(status_code=401, detail="로그인 필요")
    try:
        claims = auth.verify_id_token(token)
        if not claims.get("uid"):
            raise ValueError("uid missing")
        return claims["uid"]
    except (auth.ExpiredIdTokenError, auth.InvalidIdTokenError, ValueError):
        raise HTTPException(status_code=401, detail="로그인 유효기간 만료")
    except Exception:
        raise HTTPException(status_code=503, detail="인증 확인 불가")


def quota(uid: str, endpoint: str):
    """Transactional per-UID limits across Cloud Run instances. Fail closed."""
    now = datetime.now(timezone.utc)
    day, minute = now.strftime("%Y%m%d"), now.strftime("%Y%m%d%H%M")
    id_hash = hashlib.sha256(f"{PROJECT}:{uid}:{day}".encode()).hexdigest()
    ref = db.collection("apiGatewayUsage").document(id_hash)
    weight = 5 if endpoint == "transit" else 2 if endpoint == "walking" else 1

    @firestore.transactional
    def consume(tx):
        snap = ref.get(transaction=tx)
        old = snap.to_dict() if snap.exists else {}
        minute_count = old.get("minuteCount", 0) if old.get("minute", "") == minute else 0
        units = old.get("units", 0)
        routes = old.get("transitCount", 0)
        walks = old.get("walkingCount", 0)
        if (minute_count >= PER_MINUTE or units + weight > DAILY_UNITS or
                (endpoint == "transit" and routes >= TRANSIT_PER_DAY) or
                (endpoint == "walking" and walks >= WALKING_PER_DAY)):
            return False
        tx.set(ref, {"minute": minute, "minuteCount": minute_count + 1,
                     "units": units + weight,
                     "transitCount": routes + int(endpoint == "transit"),
                     "walkingCount": walks + int(endpoint == "walking"),
                     "expiresAt": now + timedelta(days=3)})
        return True

    try:
        if not consume(db.transaction()):
            raise HTTPException(status_code=429, detail="요청 한도 초과")
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=503, detail="요청 처리 불가")


def secret(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise HTTPException(status_code=503, detail="서비스 이용 불가")
    return value


def upstream(method: str, url: str, *, headers=None, params=None, payload=None):
    """All URLs are authored below; never accept arbitrary upstream URLs."""
    try:
        with httpx.Client(timeout=TIMEOUT, follow_redirects=False,
                          trust_env=False) as client:
            with client.stream(method, url, headers=headers, params=params,
                               json=payload) as response:
                if response.status_code != 200:
                    raise HTTPException(status_code=502, detail="외부 정보 조회 오류")
                parts, used = [], 0
                for block in response.iter_bytes():
                    used += len(block)
                    if used > MAX_RESPONSE:
                        raise HTTPException(status_code=502, detail="응답 크기 초과")
                    parts.append(block)
        data = json.loads(b"".join(parts))
        if not isinstance(data, dict):
            raise ValueError("invalid JSON")
        return JSONResponse(content=data)
    except HTTPException:
        raise
    except httpx.TimeoutException:
        raise HTTPException(status_code=504, detail="연결 시간 초과")
    except (httpx.TransportError, ValueError, UnicodeError):
        raise HTTPException(status_code=502, detail="외부 정보 조회 오류")


@app.get("/healthz")
def health():
    return {"status": "ok"}


@app.middleware("http")
async def check_size(request: Request, call_next):
    if request.method in ("POST", "PUT", "PATCH"):
        try:
            length = int(request.headers.get("content-length", "0"))
            if length > 10_000:
                return JSONResponse(status_code=413, content={"detail": "요청 크기 초과"})
        except ValueError:
            return JSONResponse(status_code=400, content={"detail": "잘못된 요청"})
    return await call_next(request)


@app.get("/v1/pois")
def pois(q: str = Query(min_length=2, max_length=70),
         uid: str = Depends(require_user)):
    quota(uid, "pois")
    return upstream("GET", "https://apis.openapi.sk.com/tmap/pois",
                    headers={"appKey": secret("TMAP_APP_KEY")},
                    params={"version": 1, "searchKeyword": q,
                            "searchType": "all", "searchtypCd": "A",
                            "count": 10, "page": 1,
                            "resCoordType": "WGS84GEO", "reqCoordType": "WGS84GEO"})


@app.post("/v1/transit/routes")
def transit(body: TransitRequest, uid: str = Depends(require_user)):
    quota(uid, "transit")
    key = os.getenv("TMAP_TRANSIT_APP_KEY") or secret("TMAP_APP_KEY")
    values = body.model_dump()
    for field in ("startX", "startY", "endX", "endY"):
        values[field] = str(values[field])
    values.update({"count": 3, "lang": 0, "format": "json"})
    return upstream("POST", "https://apis.openapi.sk.com/transit/routes",
                    headers={"appKey": key}, payload=values)


@app.post("/v1/walking")
def walking(body: WalkingRequest, uid: str = Depends(require_user)):
    quota(uid, "walking")
    return upstream("POST", "https://apis.openapi.sk.com/tmap/routes/pedestrian",
                    params={"version": 1}, headers={"appKey": secret("TMAP_APP_KEY")},
                    payload=body.model_dump())


@app.get("/v1/weather")
def weather(base_date: str = Query(pattern=r"^\d{8}$"),
            base_time: str = Query(pattern=r"^\d{4}$"),
            nx: int = Query(ge=1, le=149),
            ny: int = Query(ge=1, le=253),
            uid: str = Depends(require_user)):
    quota(uid, "weather")
    return upstream("GET",
                    "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst",
                    params={"serviceKey": unquote(secret("KMA_SERVICE_KEY")),
                            "pageNo": 1, "numOfRows": 1000, "dataType": "JSON",
                            "base_date": base_date, "base_time": base_time,
                            "nx": nx, "ny": ny})


@app.get("/v1/subway")
def subway(station: str = Query(min_length=1, max_length=30),
           uid: str = Depends(require_user)):
    if not re.fullmatch(r"[가-힣A-Za-z0-9() .·-]+", station):
        raise HTTPException(status_code=422, detail="역 이름 확인 필요")
    quota(uid, "subway")
    key = quote(secret("SEOUL_SUBWAY_API_KEY"), safe="")
    # Provider has documented HTTP; Android-to-server communication is HTTPS.
    url = ("http://swopenAPI.seoul.go.kr/api/subway/" + key +
           "/json/realtimeStationArrival/0/30/" + quote(station, safe=""))
    return upstream("GET", url)


@app.get("/v1/bus/stops")
def bus_stops(lat: float = Query(ge=33, le=39),
              lon: float = Query(ge=124, le=132),
              uid: str = Depends(require_user)):
    quota(uid, "bus")
    return upstream("GET",
                    "https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList",
                    params={"serviceKey": unquote(secret("TAGO_BUS_SERVICE_KEY")),
                            "_type": "json", "pageNo": 1, "numOfRows": 100,
                            "gpsLati": lat, "gpsLong": lon})


@app.get("/v1/bus/arrivals")
def bus_arrivals(cityCode: str = Query(pattern=r"^[0-9]{1,10}$"),
                 nodeId: str = Query(pattern=r"^[A-Za-z0-9]{1,80}$"),
                 uid: str = Depends(require_user)):
    quota(uid, "bus")
    return upstream("GET",
                    "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList",
                    params={"serviceKey": unquote(secret("TAGO_BUS_SERVICE_KEY")),
                            "_type": "json", "pageNo": 1, "numOfRows": 100,
                            "cityCode": cityCode, "nodeId": nodeId})

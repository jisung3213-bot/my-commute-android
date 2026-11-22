# 나만의 출퇴근 — API 연동 명세·실측 응답·검증 방법

기준: **2026-11-19 04:37 KST** Android DEBUG / SDK 34 실제 진단 로그. 이 문서는 **검증된 사실과 아직 미검증인 기능을 분리**한다. 인증키·요청 원문·개인 경로는 기록하지 않는다.

## 공통 설계 원칙

1. **API 연결(HTTP 200) ≠ 정상 스키마 ≠ 실제 ETA 확보 ≠ 루틴 매칭·알림 성공**. 네 단계를 별도로 판정한다.
2. **공공데이터포털 동일 키 공유:** `KMA_SERVICE_KEY`, `TAGO_BUS_SERVICE_KEY`, `SEOUL_BUS_SERVICE_KEY`는 같은 값이어도 정상이다. 선택적으로 `PUBLIC_DATA_SERVICE_KEY`를 공통 기본값으로 사용하되 개별 키가 지정되어 있으면 우선한다. **서비스별 활용승인/권한은 키 문자열의 동일성과 별개**이다.
3. 서울 지하철 인증키(`SEOUL_SUBWAY_API_KEY`)와 TMAP 키(`TMAP_APP_KEY`, `TMAP_TRANSIT_APP_KEY`)는 해당 제공자별 키를 사용한다. `local.properties` 파일은 Git에 올리지 않는다. 현재 Gradle `BuildConfig` 방식은 **시연용으로 APK에 키가 들어간다**; 상용 배포는 서버 프록시 및 호출량·권한 통제가 필요하다.
4. 제공자 오류는 **HTTP 오류 / 응답 내 제공자 코드 / 유효한 빈 데이터**로 나눠 로그에 남긴다. `INFO-200`이나 `totalCount=0`을 곧바로 키 실패로 분류하지 않는다.
5. 운영 중에는 유효기간과 새로고침 정책을 준수한다. 디버그 진단은 별도 15분 쿨다운 없이 반복 실행 가능하지만 **실행당 최대 9건**, 제공기관 제한은 그대로 적용된다.
6. 실시간 이동 기능의 실제 경로 매칭은 정류장·역 이름, 노선 ID, 방향, 데이터 생성시각을 검증한다. 근거가 부족하면 보류 상태를 표시하고 가짜 ETA를 생성하지 않는다.

## A. 서울시 버스 정류소조회·도착정보 (서울 BIS)

- **도메인/형식:** `http://ws.bus.go.kr/api/rest/`, REST GET, **XML 응답**. 서울 버스 호출 시 `Accept: application/xml`.
- **공통 요청:** `serviceKey` 사용. 인코딩된 키/디코딩된 키를 중복 인코딩하지 않도록 정규화한다.
- **정류소명 검색:** `stationinfo/getStationByName?serviceKey=...&stSrch=서울역`.
- **정류소 경유 노선 확인:** `stationinfo/getRouteByStation?serviceKey=...&arsId=...`. 정류장 검색 결과의 ARS 번호와 노선 ID를 연결한다.
- **노선별 도착정보:** `arrive/getArrInfoByRouteAll?serviceKey=...&busRouteId=...` (사용자 제공 2025-08-14 활용가이드). 응답의 `itemList`에서 정류소 ID를 찾아 `exps1`, `exps2`(초 단위)만 시간으로 사용한다. 단위가 확실하지 않은 `traTime`은 ETA로 사용하지 않는다.
- **안전 검증:** XML DTD/ENTITY 금지, 외부 엔터티 차단, 응답 길이 최대 3.2MB, `headerCd=0` 판정. 방향·동명이역 정류장·순환 노선 등 매칭이 모호하면 보류한다.
- **실측(04:37):** 정류소 `HTTP=200 MEDIA_TYPE=XML PROVIDER_CODE=0 ITEM_COUNT=24`, 도착정보 `HTTP=200 MEDIA_TYPE=XML PROVIDER_CODE=0 ITEM_COUNT=104`, 각 첫 행 필수 필드 스키마 통과.
- **주의:** 위 수치는 고정 샘플 기준이다. 실제 사용자 루틴에서 노선·정류소를 결합해 정확한 차량 ETA를 표시하는 **종단간 성공은 미검증**이다.
- **과거 오류와 해결:** `401`(접근 거부), `406`(요청/응답 형식 불일치 가능), `ParserConfigurationException`(Android XML 파서 호환성)은 공식 엔드포인트·XML Accept·공통 `SeoulXmlParser` 사용 후 실측에서 재현되지 않음.

## B. 서울시 지하철 실시간 도착정보

- **명세:** 서울 열린데이터광장 `realtimeStationArrival` 요청변수는 `KEY`, `TYPE`, `SERVICE`, `START_INDEX`, `END_INDEX`, `statnNm`.
- **요청 예시:** `http://swopenAPI.seoul.go.kr/api/subway/{key}/json/realtimeStationArrival/1/30/서울` (역명 URL 인코딩).
- **역명:** 공식 역정보에서 예컨대 `서울`, `강남`, `광운대` 사용. 여러 호선이 같은 역명을 공유하므로 응답 `subwayId` 및 `statnNm`으로 일치시킨다. 역명 뒤 `역` 접미사는 앱 내부에서 정규화한다.
- **출력 필드:** `realtimeArrivalList`의 `subwayId`, `statnNm`, `trainLineNm`(행선지/방향), `barvlDt`(남은 초), `recptnDt`(생성 시각), `arvlCd`(상태), `arvlMsg2`, `bstatnNm`, `lstcarAt`.
- **시간 처리:** 생성 후 경과시간을 `barvlDt`에서 차감하고, 오래된 응답 및 방향 미확정 열차는 실제 탑승 ETA로 주장하지 않는다. 역과 노선만 맞고 다음 역 방향 검증이 불가능하면 보류한다.
- **오류 코드:** `INFO-000` 정상, `INFO-100` 키 오류, **`INFO-200` 조회 데이터 없음**, `ERROR-300` 필수값, `ERROR-301` TYPE, `ERROR-310` SERVICE, `ERROR-331/332` 페이징, `ERROR-333/334` 요청 인덱스, `ERROR-335/336` 조회량 제한, `ERROR-500/600/601` 서버/DB 계열.
- **실측(04:37):** `/1/30/`으로 서울·강남·광운대 세 역을 순차 조회, 모두 `HTTP=200`, `code=INFO-200`, `realtimeArrivalList=0`. **새벽 시간대의 데이터 부재 가능성**이 있지만 인증 오류 또는 API 전체 장애라고 단정할 수 없다.
- **진단 방식:** 역 1개가 실시간 행을 반환하면 추가 샘플 조회를 중단한다. 모든 역이 `INFO-200`이면 실패가 아닌 **조회 데이터 없음**으로 집계한다. 실제 열차 운행 시간대에서 유효한 `realtimeArrivalList` 및 방향 매칭은 아직 미검증.

## C. TAGO 버스 (국토교통부 공공데이터)

- **도메인:** `https://apis.data.go.kr/1613000/`, REST/JSON, `serviceKey`, `_type=json`.
- **좌표로 정류소 조회:** `BusSttnInfoInqireService/getCrdntPrxmtSttnList` + `gpsLati`, `gpsLong`; 정류소는 `citycode`, `nodeid`, `nodenm`, `gpslati`, `gpslong` 식별.
- **정류소 도착정보:** `ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList` + `cityCode`, `nodeId`.
- **성공 판정:** `response.header.resultCode=00`과 `body.totalCount` 및 `body.items.item` 구조 확인. `HTTP=200 totalCount=0`은 **유효한 빈 결과**이다.
- **실측(04:37):** 서울 샘플 정류소 0건, 수원 샘플 정류소 35건(필수 필드 존재), 수원 샘플 도착정보 0건. **실시간 ETA 수신 성공은 미검증.**
- **앱 동작:** 서울권 버스에는 서울 BIS를 우선 사용하고, 이외 구간에는 TAGO를 사용한다. 권역 경계는 현재 근사 좌표 범위라 지자체 경계/연계 노선의 완전한 판별이 아니다.

## D. 기상청 단기예보

- **요청:** `https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst`, `serviceKey`, `dataType=JSON`, `base_date`, `base_time`, `nx`, `ny`.
- **응답:** `response.header.resultCode=00`, `body.items.item`; `category`, `fcstDate`, `fcstTime`, `fcstValue` 필드 검증.
- **핵심 범주:** `TMP` 기온, `PTY` 강수형태, `POP` 강수확률. 예보 기준시각과 실제 목적 시간대를 구분하고 미조회/예보 범위 밖일 때는 임의의 맑음 값을 생성하지 않는다.
- **실측(04:37):** `HTTP=200`, `resultCode=00`, **944건**, TMP/PTY/POP 각각 **78건**, 범위 `202611190300..202611230000`. 정상.

## E. TMAP

- `TMAP_APP_KEY`: 지도 SDK/POI/보행 관련 호출. `TMAP_TRANSIT_APP_KEY`: 대중교통 경로 탐색.
- 경로 후보, 보행 및 환승 구간은 TMAP 응답에 기반해 표기한다. 이번 진단은 TMAP을 호출하지 않았으므로 **키 동작/SDK 연동/경로 정확도는 이번 결과의 검증 범위 밖**.
- 실제 ETA와 계획 이동시간을 구분한다. 유료/제한 호출을 화면 반복 렌더링마다 새로 요청하지 않는다.

## F. 앱 전체 연동 검증 순서

1. **요청 구성:** 키 존재·URL 인코딩·서비스별 `Accept`·필수 변수·페이징·지역 좌표 확인.
2. **전송 계층:** HTTP 상태, 응답 미디어 유형, 요청 시간, 응답 크기 기록. 오류 원문/키/전체 URL은 보고서에 포함하지 않는다.
3. **제공자 계층:** JSON/XML 정상 파싱 후 `INFO-000/INFO-200`, `resultCode`, `headerCd` 등 제공자별 코드를 확인한다.
4. **필드 계층:** 배열/행 건수 및 필요한 ID·역명·호선·노선·시간 필드의 존재·단위를 검사한다.
5. **실제 경로 계층:** 저장한 출퇴근 루틴의 역/정류장·노선·진행 방향과 수신 행이 정확히 일치하는지 확인한다.
6. **시간 계층:** 예보 발표시각, 열차 생성시각, 도착시간 유효기간을 반영해 만료된 정보를 제거한다.
7. **알림 계층:** GPS·보행시간·차량도착시간·권한·백그라운드 상태까지 실기기에서 확인한다. **현재 1~4단계 일부만 실측 통과**했으며 5~7단계는 별도 검증 대상이다.

## 문서 출처 및 증거

- 사용자가 제공한 **서울특별시 버스도착정보조회 서비스 활용가이드(2025-08-14)**.
- 사용자가 제공한 **서울 지하철 실시간 도착정보 요청변수·출력필드·메시지 설명 화면** 및 **실시간도착 역정보(2026-09-02) 엑셀**.
- 사용자 실기기 **MY-COMMUTE LIVE API PROBE** 로그(2026-11-19 03:13~04:37 KST).
- 코드: `ApiProbe.java`, `LiveArrivalClient.java`, `SeoulBusClient.java`, `SeoulXmlParser.java`, `KmaWeatherClient.java`, `ArrivalMatcher.java`.
- 서울 지하철 공식 서비스: https://data.seoul.go.kr/dataList/OA-12764/F/1/datasetView.do
- 서울시 Open API 이용안내: https://data.seoul.go.kr/together/guide/useGuide.do

**보안:** 이 문서에는 어떤 API 키 값도 포함하지 않는다.

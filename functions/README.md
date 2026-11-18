# Firebase 기반 API 서버 설정 안내

**외부 API는 Firestore에서 직접 호출하지 않아.** Firebase Authentication으로 로그인한 앱이 Cloud Functions에 요청하고, Cloud Functions가 비밀키로 TMAP·기상청·서울시·TAGO API를 호출해. Firestore는 사용자 루틴·환경설정과 서버 요청 횟수만 저장해.

## 준비 사항

- 기존 앱에 연결된 **Firebase 프로젝트**(Authentication, Firestore)를 그대로 사용해.
- Firebase Console에서 요금제를 **Blaze(사용량 기반)**로 변경하고 결제 계정을 연결해야 Functions를 배포할 수 있어. 무료 사용량이 있지만 초과 시 과금될 수 있어.
- Node.js 22 및 Firebase CLI를 설치해. `npm install -g firebase-tools`를 실행해.
- **서버 인증키** 5종 준비: TMAP 장소·도보, TMAP 대중교통, 기상청 단기예보, 서울시 지하철, TAGO 버스. TAGO 버스정류소정보와 버스도착정보는 각각 활용승인을 확인해.

## 최초 1회 배포 순서

1. 프로젝트 루트에서 `firebase login` 실행.
2. `firebase use --add`로 **Android 앱에 연결된 Firebase 프로젝트**를 선택해. `.firebaserc`는 배포 전 사용자가 지정해.
3. 아래 명령으로 비밀키를 각각 등록해. 명령 실행 후 실제 키를 입력하면 돼. GitHub나 Android `local.properties`에 데이터 API 키를 기록하지 마.

```bash
firebase functions:secrets:set TMAP_APP_KEY
firebase functions:secrets:set TMAP_TRANSIT_APP_KEY
firebase functions:secrets:set KMA_SERVICE_KEY
firebase functions:secrets:set SEOUL_SUBWAY_API_KEY
firebase functions:secrets:set TAGO_BUS_SERVICE_KEY
```

4. `cd functions` → `npm install` → `cd ..` 순서로 의존성을 설치해.
5. `firebase deploy --only functions,firestore:rules`를 실행해. 첫 배포에는 Google Cloud APIs 활성화와 권한 설정 등 Firebase에서 안내하는 사전 작업이 필요할 수 있어.
6. 앱을 Android Studio에서 **Gradle Sync 후 재설치**해. 앱의 `google-services.json`에 포함된 Firebase 프로젝트 ID로 **`asia-northeast3` 지역의 `commuteApi` 함수 주소를 자동 생성**하므로 보통 서버 URL을 별도로 입력하지 않아도 돼.

## 앱의 설정 파일

`local.properties`에는 SDK 경로와 지도 SDK용 키만 필요해.

```properties
# 지도 SDK를 실제로 사용하는 경우에만 설정
TMAP_MAP_SDK_KEY=발급받은_지도_SDK_키
```

데이터 API 키는 모두 Firebase Functions Secrets에 저장해. **Firestore 문서나 Firebase Remote Config, Android BuildConfig에 비밀키를 넣지 않아.**

## 정상 작동 확인

- 함수 주소 `https://asia-northeast3-프로젝트ID.cloudfunctions.net/commuteApi/healthz`를 열면 `{"status":"ok"}`가 표시돼야 해.
- 같은 함수의 `/v1/pois`를 로그인 토큰 없이 요청하면 **401**이 반환돼야 해.
- 앱에서 로그인 → 장소 검색 → 대중교통 경로 검색 → 날씨 조회 → 지하철·버스 조회 순서로 확인해.
- Cloud Functions 로그와 Firebase 사용량·Google Cloud 예산 알림을 확인해. 공식 서비스 이용약관과 API별 활용 승인 및 호출량도 별도로 확인해야 해.

## 이용 구조와 주의점

- 함수 URL 자체는 공개 HTTPS지만, `/v1/` API들은 **Firebase ID 토큰 검증과 Firestore 사용자별 요청량 제한**을 거쳐.
- 사용자별 기본 제한: **분당 20회, 하루 가중치 250, 대중교통 하루 20회, 도보 하루 50회**. 실제 정식 서비스에서는 비용·이용량 관측 후 조정해야 해.
- 현재 기상청·버스·지하철 응답에는 공통 사용자 간 공유 캐시가 없어. 사용자가 늘면 캐싱과 공급자별 이용량 제한을 별도로 설계해야 해.
- 서울시 지하철 제공기관으로 연결하는 서버 내부 구간은 문서화된 HTTP 주소를 사용하고 있어. 앱과 Firebase 함수 간 통신은 HTTPS지만 **제공기관까지 전 구간 암호화된 것은 아니야**.
- 실제 Firebase 배포, 요금제 변경, 인증키 업로드는 GitHub에 코드를 올리는 것만으로 이뤄지지 않아.
- 기존 `backend/` 폴더는 Cloud Run용 이전 구조라 사용하지 않아. **Firebase Functions 구현은 `functions/index.js`가 기준**이야.

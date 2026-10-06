# b-deploy-5xx 장애 분석

대상: `incident-logs/b-deploy-5xx/` (2026-09-17 로그 2개 + 배포 이력 1개)

> 민감값은 맨 아래 마스킹 규칙으로 가렸다. 원본 `deploy-history.md` 에는 배포 API Bearer 토큰이 평문으로 들어 있다(`[TOKEN]`) — 별도 조치 대상.

## 요약

- **무엇이**: 2026-09-17 13:46:38 ~ 14:34:45 KST(로그 끝 14:39:58 이후는 미확인), 학급 1 의 재배포 API `POST /api/distributions/{42,43}/redistribute` 가 500 을 39번 냈다(13:42 이후 재배포 요청 49건 중 39건). 13:41:51 ~ 13:41:59 재기동 중에는 502 가 8건 있었다.
- **직접 원인**: v1.4.2 에서 재배포 응답에 "같은 학급 배포 이력"(`listByClass`)을 붙였다. 학급 1 의 배포 중 하나가 없는 assignment 9 를 가리켜, 이력 목록을 로드하다 실패한다.
- **배경 원인**: 이 고아 참조는 배포 전부터 있었다(09:52 부터 distribution 41 에서 같은 예외 7건). 시드 데이터로만 검증해 놓쳤다.
- **확신 수준**: 높음. 배포 후 500 39/39 의 스택 · 예외가 같고, 학급 2 · 3 재배포는 정상이다. 고아 배포가 41 인지, 500 을 받은 재배포가 DB 에 반영됐는지, 언제 끝났는지는 미확인(9절).


## 1. 수집 범위

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 (예) | 타임존 |
|---|---:|---|---|---|
| `app.log` | 2,938 (타임스탬프 줄 1,275 · 스택 트레이스 연속 줄 1,663) | 2026-09-17 09:00:04 ~ 14:39:57 | ISO 8601 + 오프셋 `2026-09-17T09:00:04.878+09:00` | KST (`+09:00` 명시, 1,275줄 전부) |
| `nginx-access.log` | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | combined `[17/Sep/2026:09:00:00 +0900]` | KST (`+0900` 명시, 20,365줄 전부) |
| `deploy-history.md` | 39 | 2026-09-01 ~ 2026-09-17 (배포 4건) | `2026-09-17 13:40:05` (오프셋 없음) | KST (문서 3행에 "모두 KST" 명시) |

**KST 변환 규칙**: 세 파일 모두 이미 KST 라 변환이 필요 없다. 비교할 때는 형식만 맞춘다.
- `app.log`: `YYYY-MM-DDTHH:MM:SS.mmm+09:00` → 앞 19자(`YYYY-MM-DDTHH:MM:SS`)를 그대로 쓴다.
- `nginx-access.log`: `[DD/Mon/YYYY:HH:MM:SS +0900]` → `Mon` 을 월 숫자로 바꿔 `YYYY-MM-DD HH:MM:SS`. 초 단위까지만 있다(밀리초 없음).
- `deploy-history.md`: 오프셋 없는 `YYYY-MM-DD HH:MM:SS` 를 KST 로 읽는다.
- 이후 다른 출처(DB · 인프라 로그 등)를 추가로 받으면 그 파일의 타임존부터 확인한다. UTC 면 +9시간.

참고: nginx 에는 응답 시간(`$request_time` · `$upstream_response_time`) 필드가 없다. 로그 형식만으로는 지연을 볼 수 없다.

### 1.2 이상 신호로 센 줄의 정의

| 파일 | 센 것 | 전체 건수 | 비고 |
|---|---|---:|---|
| `app.log` | 레벨 `ERROR` (타임스탬프 줄만) | 46 | |
| `app.log` | 레벨 `WARN` | 35 | 하루 내내 분당 0~2건으로 고르게 분포 → 배경 신호 |
| `app.log` | `timeout` · `timed out` | 0 | |
| `nginx-access.log` | 5xx (`500` 46 · `502` 8) | 54 | `500` 건수 = `app.log` ERROR 건수(46)이고 분 단위로도 일치 |
| `nginx-access.log` | 4xx (`404` 969 · `409` 35 · `400` 29) | 1,033 | 하루 내내 10분당 15~40건 → 배경 신호. `409` 건수 = `app.log` WARN 건수(35) |
| `nginx-access.log` | 타임아웃(`504`) | 0 | |

스택 트레이스 연속 줄(1,663)은 레벨이 없으므로 세지 않았다.

### 1.3 분 단위 건수

**배경 수준 (09:00 ~ 13:39, 280분)**

- nginx 분당 요청 40 ~ 82 (중앙값 59). 13:40 이후에도 같은 범위 → 트래픽 변화 없음.
- 5xx 는 4개 분에만 총 7건: 09:52 (3) · 10:31 (1) · 11:17 (1) · 11:18 (2). 모두 `500`, 같은 분에 app ERROR 가 같은 수로 있다. → **분당 평균 0.025건, 5xx 0건인 분이 98.6%**.
- app WARN 은 분당 0~2건.

**배포 전후 (13:36 ~ 13:48)** — nginx 요청 / 5xx / 4xx, app ERROR, app 이벤트

| 분 | 요청 | 5xx | 4xx | app ERROR | app 이벤트 |
|---|---:|---:|---:|---:|---|
| 13:36 | 63 | 0 | 5 | 0 | |
| 13:37 | 62 | 0 | 6 | 0 | |
| 13:38 | 76 | 0 | 6 | 0 | (배포 이력: 승인 13:38) |
| 13:39 | 67 | 0 | 2 | 0 | |
| 13:40 | 73 | 0 | 4 | 0 | (배포 이력: v1.4.2 배포 시작 13:40:05) |
| 13:41 | 72 | **8 (502)** | 4 | 0 | 13:41:48 graceful shutdown 시작 → 13:41:55 v1.4.2 기동 시작 |
| 13:42 | 69 | 0 | 2 | 0 | 13:42:00 기동 완료 (배포 이력: 배포 완료 13:42:31) |
| 13:43 | 59 | 0 | 3 | 0 | |
| 13:44 | 58 | 0 | 1 | 0 | |
| 13:45 | 59 | 0 | 0 | 0 | (app 로그 줄 없음) |
| 13:46 | 56 | **2** | 2 | **2** | 첫 ERROR 13:46:38 |
| 13:47 | 58 | **1** | 2 | **1** | |
| 13:48 | 68 | 0 | 3 | 0 | |

`502` 8건은 13:41:51 ~ 13:41:59 (재기동 중 9초)에만 있고 app 로그에는 대응 줄이 없다.

**13:46 이후 5xx(=`500`, = app ERROR)가 있는 분** — 54분 중 20분

13:46 (2) · 13:47 (1) · 13:50 (4) · 13:52 (1) · 13:53 (1) · 13:55 (3) · 13:58 (2) · 14:02 (2) · 14:03 (4) · 14:06 (1) · 14:11 (3) · 14:15 (1) · 14:16 (2) · 14:22 (3) · 14:27 (2) · 14:28 (1) · 14:31 (2) · 14:32 (1) · 14:34 (3)

**10분 단위 요약**

| 구간 | 요청 | 5xx | app ERROR | app WARN |
|---|---:|---:|---:|---:|
| 09:00 ~ 13:39 (10분 평균) | 597 | 0.25 | 0.25 | 1.1 |
| 13:40 ~ 13:49 | 625 | 11 (502 8 · 500 3) | 3 | 0 |
| 13:50 ~ 13:59 | 549 | 11 | 11 | 1 |
| 14:00 ~ 14:09 | 613 | 7 | 7 | 0 |
| 14:10 ~ 14:19 | 665 | 6 | 6 | 0 |
| 14:20 ~ 14:29 | 605 | 6 | 6 | 1 |
| 14:30 ~ 14:39 | 581 | 6 | 6 | 2 |

구간 합계: 13:46 ~ 14:39 (54분) 5xx 39건, 분당 0.72건 — 배경(0.025)의 약 29배. 14:00 이후로도 10분당 6~7건으로 줄어드는 추세가 뚜렷하지 않다.

### 1.4 이상 구간 제안

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 시작 | **13:41:51** (`502` 첫 건) | 직전 5xx 는 11:18 이고 11:19 ~ 13:40(142분) 동안 0건. 재기동 구간 502 와 그 뒤 500 지속 구간을 하나로 묶어 둔다. |
| 시작 (500 기준) | 13:46:38 | 재기동 후 첫 `500` · 첫 ERROR. 13:42 ~ 13:45 은 4분간 5xx 0건. |
| 끝 | **확정 불가 — 로그 끝(14:39:58)까지 지속** | 마지막 `500` 은 14:34:45 지만, 그 뒤 로그 끝까지 5분 13초뿐이다. 13:46 이후 오류 사이 간격이 최대 5분(14:06 → 14:11, 14:16 → 14:22)이라 14:35 ~ 14:39 의 0건은 "종료"의 증거가 되지 못한다. 배포 이력에도 v1.4.2 이후 롤백 · 재배포 기록이 없다. |

### 1.5 앞뒤 여유

| 방향 | 제안 | 범위 (KST) | 이유 |
|---|---|---|---|
| 앞 | **60분** | 12:40 ~ | 배포 승인(13:38) · 시작(13:40:05) 직전 상태를 충분히 포함. 배경 비교는 09:00 ~ 13:39 전체(280분)를 그대로 쓴다. 필요하면 직전 배포 v1.4.1(2026-09-14 18:05 ~ 18:07)의 전후 30분 로그도 비교군으로 요청한다. |
| 뒤 | **15분 이상 — 오류가 15분 연속 0건이 될 때까지** | ~ 14:50 이후 (확보 필요) | 현재 로그가 14:39:58 에서 끝나 뒤쪽 여유가 0이다. 14:40 이후 `app.log` · `nginx-access.log` 를 추가로 받아 끝 시각을 확정해야 한다. |

**제안 수집 범위: 2026-09-17 12:40 ~ (5xx 가 15분 연속 0건인 시점 + 15분), KST.** 보유 로그로는 12:40 ~ 14:39:58 까지만 채워진다.

### 1.6 다음 단계 작업 시 주의

- `app.log` 는 13:41:48 ~ 13:42:00 사이 프로세스가 재시작됐다. 이 전후로 스레드 이름 · 풀 상태가 이어지지 않는다.
- 배경 409/404/WARN 은 배포 전후로 수준이 같다. 이상 신호 집계에서 빼고 본다.
- 배포 전 `500` 7건(09:52 · 10:31 · 11:17 · 11:18)도 같은 날 있었다. 배포 후 `500` 과 같은 종류인지는 2절에서 구분한다.
- 대기 호스트 `api-2` 는 평소 정지라 로그가 없다. 이 로그는 모두 `api-1` 한 대의 것으로 본다.

### 집계 방법 (재현용)

```bash
cd incident-logs/b-deploy-5xx
# 줄 수 · 앞뒤 시각
wc -l *; head -1 app.log | cut -c1-30; tail -1 nginx-access.log | cut -c1-60
# 타임존 접미사 분포
grep -oE '^[0-9T:.-]+\+[0-9:]+' app.log | grep -oE '\+[0-9:]+$' | sort | uniq -c
grep -oE '\[[^]]+\]' nginx-access.log | awk '{print $2}' | sort | uniq -c
# app.log: 레벨 분포 · 분 단위 ERROR/WARN (타임스탬프 줄만)
awk '{print $2}' app.log | grep -E '^(INFO|WARN|ERROR)$' | sort | uniq -c
awk '/^20[0-9][0-9]-/{m=substr($1,12,5); if($2=="ERROR")e[m]++; if($2=="WARN")w[m]++} END{for(k in e)print k,e[k]; for(k in w)print k,"W",w[k]}' app.log | sort
# nginx-access.log: 상태코드 분포 · 분 단위 요청/5xx/4xx
awk '{print $9}' nginx-access.log | sort | uniq -c
awk '{m=substr($4,14,5); t[m]++; if($9>=500)f[m]++; if($9>=400&&$9<500)c[m]++} END{for(k in t)print k,t[k],f[k]+0,c[k]+0}' nginx-access.log | sort
# 타임아웃
grep -ciE 'timeout|timed out' app.log nginx-access.log
# 재기동 이벤트
grep -nE 'GracefulShutdown|Starting ItemBankApplication|Started ItemBankApplication' app.log | cut -c1-120
```

## 2. 5xx 발생 API 와 배포 변경의 연결 고리

### 2.1 5xx 가 난 API (nginx `500`, 경로의 숫자는 `{n}` 으로 묶음)

| 구간 | API | 500 건수 | 같은 분의 app ERROR 스택 (`com.example` 프레임) |
|---|---|---:|---|
| 배포 전 (~13:40) | `POST /api/distributions/{n}/redistribute` | 6 (전부 id 41) | `DistributionController.redistribute` → `DistributionService.redistribute` → `loadOrThrow` |
| 배포 전 | `GET /api/distributions/{n}` | 1 (id 41) | `DistributionController.getDistribution` → `DistributionService.getDistribution` → `loadOrThrow` |
| 배포 후 (13:42~) | `POST /api/distributions/{n}/redistribute` | 39 (id 42: 19 · id 43: 20) | `DistributionController.redistribute` → **`DistributionService.listByClass`** |

- 46건 전부 예외가 같다: `JpaObjectRetrievalFailureException` / `Caused by: FetchNotFoundException: Entity Assignment with identifier value 9 does not exist`.
- 그 밖의 API 에는 13:42 이후 5xx 가 없다. 4xx 는 분당 3.05건으로 배포 전과 같다.

### 2.2 배포 변경 (`deploy-history.md` v1.4.2) ↔ 코드 (`modern/api`)

| 배포 이력의 변경 | 코드 위치 | 로그에서 본 것 |
|---|---|---|
| 재배포 응답에 같은 학급의 배포 이력 추가. `DistributionController.redistribute` 가 `listByClass(classId)` 를 호출 | `DistributionController.java:38-41`: `redistribute(id, reason)` 다음에 `listByClass(redistributed.classId())` | 배포 후 500 39건 모두 스택이 `listByClass` 에서 시작 |
| (기존) `listByClass` 는 학급의 배포를 `assignment` 까지 미리 로드 | `DistributionRepository.java:13-14` `@EntityGraph({"assignment","classRoom"}) findByClassRoomIdOrderByDistributedAtAsc` · `DistributionService.java:36-41` | 학급의 배포 중 하나라도 없는 assignment 를 가리키면 목록 전체가 실패 |
| 테스트: 스테이징 DB(시드)에서 재배포 3건 수동 확인 | `db/mariadb/init/02-seed.sql:119-138`: assignment 1~6, distribution 1~8 뿐이고 `01-schema.sql:101` 에 FK 있음 | 운영 로그의 distribution 41~44 · assignment 9 는 시드에 없다 |

## 3. 가설과 검증

판정: **유지** = 로그가 가설과 맞고 반대 증거 없음 / **기각** = 로그가 가설과 어긋남 / **판단 불가** = 이 로그만으로는 결론 못 냄.

| ID | 가설 | 확인 방법 | 실행 결과 (3.1 의 명령 번호) | 판정 | 이유 | 판단 불가 시 있었으면 하는 로그 |
|---|---|---|---|---|---|---|
| H1 | v1.4.2 에서 재배포 뒤 추가된 `listByClass(classId)` 가 학급 1 의 배포를 로드하다 **없는 assignment 9** 를 만나 500 을 낸다 | 배포 후 500 의 스택 · 예외 · 대상 학급 | ③ 39/39 가 `listByClass` 경유, ② 39/39 가 assignment 9, ⑤ 배포 후 class 1 재배포 39건 모두 바로 다음 줄 ERROR · class 2/3 재배포 5건은 200 | **유지** | 배포 후 500 전부가 새로 추가된 호출 경로 · 같은 엔티티 · 학급 1 에서만 났다 | — |
| H2 | assignment 9 를 가리키는 고아 데이터는 배포 **전부터** 있었고, 배포가 그 데이터를 읽는 경로를 넓혔다 | 배포 전 500 의 예외 · 대상 | ② 배포 전 7/7 도 assignment 9, ④ 전부 distribution 41 (재배포 6 · 단건 조회 1) | **유지** (단, 41 이 학급 1 소속인지는 판단 불가) | 같은 예외가 09:52 부터 있었고 배포 전에는 41 을 직접 열 때만 났다 | DB 조회(`distribution` 41 의 `class_id` · `assignment_id`, `assignment` 9 의 존재 · 삭제 이력) 또는 41 요청 시 class 를 남기는 앱 로그 |
| H3 | 재기동(무중단 아님) 자체가 5xx 를 냈다 | 502 시각과 재기동 시각 대조, 기동 후 5xx | ⑦ 502 8건 13:41:51~59, 재기동 13:41:48~13:42:00 안. 13:42~13:45 5xx 0, 13:46 부터는 `500` 만 | **유지(502 8건 한정) / 기각(이후 500 지속)** | 502 는 재기동 9초에만 있고, 이후 500 은 다른 예외 경로다 | — |
| H4 | 트래픽 증가 · 커넥션 풀 고갈 · 인프라 문제 | 분당 요청 수, Hikari 경고, 다른 API 오류 | 1.3 분당 요청 40~82 로 전후 같음, ⑧ Hikari/connection 경고 0, ⑥ 다른 API 5xx 0 · 4xx 분당 3.05 → 3.05 | **기각** | 부하 · 풀 지표 변화가 없고 실패가 재배포 API 하나에 몰렸다 | — |
| H5 | 500 을 받은 재배포도 **쓰기는 커밋**됐다(응답만 실패) → 사용자가 재시도하며 재배포가 중복 반영됐다 | 재배포 성공 로그와 500 의 짝, 재시도 패턴 | ⑤ 배포 후 500 39건 모두 직전에 `redistributed distribution` INFO 로그, ⑨ 39건이 클라이언트 IP 1개 · 같은 대상 연속 간격 ≤60초 25 / 61~300초 5 / >300초 7 | **판단 불가 (로그 부족)** | INFO 로그는 커밋 전에 찍혀 커밋 여부를 증명하지 못한다. 컨트롤러가 트랜잭션 밖에서 `listByClass` 를 부르므로 커밋됐을 가능성은 코드상 있다 | DB 의 `distribution` 42 · 43 행 · `redistributed` · `distributed_at` 변경 이력(binlog · general log), 또는 트랜잭션 커밋/롤백 로그(`org.springframework.transaction` DEBUG) |
| H6 | 스테이징 확인(시드 데이터)으로는 이 데이터를 재현할 수 없어 배포 전 테스트가 놓쳤다 | 시드 범위 대조 (로그 아님) | ⑩ 시드 assignment 1~6 · distribution 1~8, `assignment_id` 에 FK 있음 | **유지** (로그가 아니라 코드 · 시드로 확인) | 시드에는 고아 참조가 들어갈 수 없다 | 운영 DB 에 FK 가 실제로 걸려 있는지(스키마 덤프) |
| H7 | 장애가 14:39 이후에도 이어졌다 | 1.4 끝 시각 | 마지막 500 14:34:45, 로그 끝 14:39:58 | **판단 불가 (로그 부족)** | 남은 5분은 배포 후 오류 간격(최대 5분) 안이다 | 14:40 이후 `app.log` · `nginx-access.log`, 롤백 · 핫픽스 배포 기록 |

### 3.1 실행한 명령과 결과 (`incident-logs/b-deploy-5xx/` 에서 실행, 기준 13:41)

```bash
# ① 500 엔드포인트 배포 전/후
awk '$9==500{split($4,a,":"); t=a[2]":"a[3]; p=$7; sub(/\?.*/,"",p); gsub(/[0-9]+/,"{n}",p); print (t<"13:41"?"전":"후"), $6, p}' nginx-access.log | sort | uniq -c
#   1 전 GET /api/distributions/{n}
#   6 전 POST /api/distributions/{n}/redistribute
#  39 후 POST /api/distributions/{n}/redistribute

# ② ERROR 직후 예외 · 없는 assignment id
awk '/^2026/{e=($2=="ERROR"); t=substr($1,12,5); next} e && /^org.springframework.orm/{match($0,/value `[0-9]+`/); print (t<"13:41"?"전":"후"), substr($0,RSTART,RLENGTH); e=0}' app.log | sort | uniq -c
#   7 전 value `9`      (JpaObjectRetrievalFailureException, Caused by FetchNotFoundException 7)
#  39 후 value `9`      (같은 예외 39)

# ③ ERROR 스택의 com.example 프레임 조합
awk '/^2026/{if(k!="")print k; k=""; e=($2=="ERROR"); t=substr($1,12,5); next} e && /at com\.example/{f=$2; sub(/\(.*/,"",f); k=(k==""?(t<"13:41"?"전 ":"후 "):k" > ")f} END{if(k!="")print k}' app.log | sort | uniq -c
#   1 전 loadOrThrow > getDistribution > (CGLIB) > DistributionController.getDistribution
#   6 전 loadOrThrow > redistribute > (CGLIB) > DistributionController.redistribute
#  39 후 listByClass > (CGLIB) > DistributionController.redistribute

# ④ redistribute 대상 id × 상태
awk '$7~/redistribute/{split($4,a,":"); p=$7; sub(/\?.*/,"",p); print (a[2]":"a[3]<"13:41"?"전":"후"), p, $9}' nginx-access.log | sort | uniq -c
#   전: 41 → 500 ×6 / 42 → 200 ×5 / 43 → 200 ×12 / 44 → 200 ×17 (그 외 200 · 409)
#   후: 42 → 500 ×19 / 43 → 500 ×20, 200 ×1(13:41:20, 재기동 전) / 44 → 200 ×1 / 5·7·8 → 200 ×5 / 2·3 → 409 ×4
#   재배포 전체: 전 200 65 · 409 31 · 500 6 / 후 200 7 · 409 4 · 500 39

# ⑤ 13:42 이후 재배포 성공 로그 → 같은 스레드 다음 줄이 ERROR 인가
awk '/^2026/{t=substr($1,12,5); th=$6; if(th in p){print p[th], ($2=="ERROR"?"→ERROR":"→다른줄"); delete p[th]} if(t>="13:42" && /redistributed distribution/){match($0,/distribution [0-9]+ \(assignment [0-9]+, class [0-9]+/); p[th]=substr($0,RSTART,RLENGTH)}}' app.log | sort | uniq -c
#  19 distribution 42 (assignment 4, class 1 →ERROR
#  20 distribution 43 (assignment 6, class 1 →ERROR
#   1 distribution 44 (assignment 5, class 2 →다른줄
#   1 distribution 8  (assignment 6, class 2 →다른줄
#   2 distribution 5  (assignment 3, class 3 →다른줄
#   2 distribution 7  (assignment 5, class 3 →다른줄

# ⑥ 4xx 분당 (전 280분 / 후 59분)
awk '{split($4,a,":"); t=a[2]":"a[3]; if($9>=400&&$9<500){ if(t<"13:41")b++; else c++}} END{printf "전 %.2f / 후 %.2f\n", b/280, c/59}' nginx-access.log
#  전 3.05 / 후 3.05

# ⑦ 502 시각 범위 · 재기동 이벤트
grep '" 502 ' nginx-access.log | grep -oE '13:[0-9:]{5}' | sed -n '1p;$p'      # 13:41:51 / 13:41:59
grep -nE 'GracefulShutdown|Started ItemBankApplication' app.log | cut -c1-40   # 13:41:48 시작 · 13:41:50 완료 · 13:42:00 Started

# ⑧ 커넥션 풀 · 타임아웃 경고
grep -ciE 'hikari.*(timeout|leak|exhaust|not available)|Connection is not available' app.log   # 0
grep -ciE 'timeout|timed out' app.log nginx-access.log                                           # 0 / 0

# ⑨ 배포 후 42/43 재배포 500 의 클라이언트 · 재시도 간격
awk '$9==500 && $7~/distributions\/4[23]\/redistribute/{c[$1]++; n++} END{for(k in c){u++; if(c[k]>m)m=c[k]} print "요청",n,"IP수",u,"IP당최대",m}' nginx-access.log
#  요청 39 IP수 1 IP당최대 39   (배포 전 41 의 500 7건도 같은 IP 1개, 교사 화면 referer)
awk '$9==500 && $7~/distributions\/4[23]\/redistribute/{split($4,a,":"); s=a[2]*3600+a[3]*60+a[4]; k=$1" "$7; if(k in l){d=s-l[k]; print (d<=60?"≤60초":(d<=300?"61~300초":">300초"))} l[k]=s}' nginx-access.log | sort | uniq -c
#  25 ≤60초 / 5 61~300초 / 7 >300초

# ⑩ 시드 범위 (저장소 루트에서)
sed -n 119,138p db/mariadb/init/02-seed.sql   # assignment 1~6, distribution 1~8
grep -n 'fk_distribution_assignment' db/mariadb/init/01-schema.sql   # 101행 FK
```

참고: IP 1개는 NAT · 프록시 뒤 여러 교사일 수 있어 사용자 1명으로 단정하지 않는다.

## 4. 원인

**직접 원인** (인과 순서)

1. 09-17 13:40 ~ 13:42 v1.4.2 가 배포됐다. `DistributionController.redistribute` 가 재배포를 마친 뒤 `listByClass(classId)` 로 같은 학급의 배포 이력을 읽어 응답에 붙이도록 바뀌었다.
2. `listByClass` 는 학급의 배포 전체를 `assignment` 까지 `@EntityGraph` 로 미리 로드한다. 한 행이라도 없는 assignment 를 가리키면 목록 전체가 `FetchNotFoundException` 으로 실패한다.
3. 학급 1 의 배포 가운데 없는 assignment 9 를 가리키는 행이 있다. 그래서 13:46:38 부터 학급 1 의 재배포(42 · 43)는 매번 500 이 됐고, 학급 2 · 3 재배포는 200 이었다.
4. 재배포 자체(`DistributionService.redistribute`)는 자기 트랜잭션에서 끝난 뒤에 이력 조회가 실패한다. 사용자는 500 을 받지만 재배포는 이미 커밋됐을 수 있다(코드 구조상 가능, 로그로는 미확인).

**배경 원인**

5. assignment 9 를 가리키는 고아 배포는 배포 전부터 운영 DB 에 있었다. v1.4.1 에서도 distribution 41 을 직접 열면 같은 예외가 났다(09:52 ~ 11:18, 7건). 이 오류는 대응 없이 남아 있었다.
6. 스키마에는 `fk_distribution_assignment` FK 가 있는데도 고아 행이 있다. 운영 스키마가 저장소 스키마와 다르거나, FK 를 끈 채 데이터가 지워졌다고 볼 수 있다(미확인).
7. 배포 전 검증은 시드 데이터로만 했다. 시드에는 고아 참조가 들어갈 수 없어 이 경로를 재현하지 못했다.
8. 단일 호스트 재기동 배포라 수업 시간 중 9초간 502 가 났다. 500 지속과는 별개다.

## 5. 근거 로그 줄

경로 기준: 로그는 `incident-logs/b-deploy-5xx/`, 코드는 `modern/api/src/main/java/com/example/assignment/`. IP 는 마스킹 규칙(맨 아래)에 따라 역할 별칭으로 적었다.

| 문장 | 파일:줄 | 발췌 |
|---|---|---|
| 1 | `deploy-history.md:7` | `v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31` |
| 1 | `deploy-history.md:16` | `DistributionController.redistribute 에서 재배포 후 DistributionService.listByClass(classId) 호출 추가` |
| 1 | `DistributionController.java:38-40` | `distributionService.redistribute(id, reason)` → `distributionService.listByClass(redistributed.classId())` |
| 1 | `app.log:1264` | `13:42:00.304 … Started ItemBankApplication` |
| 2 | `DistributionRepository.java:13-14` | `@EntityGraph(attributePaths = {"assignment", "classRoom"}) … findByClassRoomIdOrderByDistributedAtAsc` |
| 2 | `app.log:1276` | `JpaObjectRetrievalFailureException: Entity Assignment with identifier value 9 does not exist` |
| 2 | `app.log:1304` | `Caused by: org.hibernate.FetchNotFoundException: … value 9 does not exist` |
| 3 | `app.log:1274` | `13:46:38.107 … redistributed distribution 42 (assignment 4, class 1)` |
| 3 | `app.log:1275` | `13:46:38.113 ERROR … unhandled exception on /api/distributions/42/redistribute` |
| 3 | `app.log:1286` · `1294` | `at DistributionService.listByClass(DistributionService.java:38)` · `at DistributionController.redistribute(DistributionController.java:40)` |
| 3 | `nginx-access.log:17155` | `교사단말A … [17/Sep/2026:13:46:38 +0900] "POST /api/distributions/42/redistribute" 500` |
| 3 | `nginx-access.log:20084` · `app.log:2887` | 마지막 500 · ERROR `14:34:45` (distribution 43) |
| 3 | `app.log:1266` · `1393` | `redistributed distribution 7 (… class 3)` · `distribution 8 (… class 2)`, 뒤에 ERROR 없음 |
| 4 | `DistributionService.java:49-61` | `@Transactional public DistributionResponse redistribute(…)` — 이력 조회와 다른 트랜잭션 |
| 5 | `app.log:194-195` | `09:52:17 ERROR … /api/distributions/41/redistribute` / `… Assignment … value 9 does not exist` |
| 5 | `app.log:205-206` | `at DistributionService.loadOrThrow(…:65)` · `at DistributionService.redistribute(…:51)` |
| 5 | `nginx-access.log:3150` · `5563` · `8468` | 41 재배포 500 (09:52:17) · `GET /api/distributions/41` 500 · 41 재배포 500 (11:18:02) |
| 6 | `db/mariadb/init/01-schema.sql:101` | `CONSTRAINT fk_distribution_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)` |
| 7 | `deploy-history.md:17` | `스테이징 DB(시드 데이터)에서 재배포 3건 수동 확인` |
| 7 | `db/mariadb/init/02-seed.sql:119-138` | assignment 1~6, distribution 1~8 |
| 8 | `deploy-history.md:18` | `단일 호스트 재기동(무중단 아님). 예상 중단 10초 안팎.` |
| 8 | `app.log:1240` · `nginx-access.log:16865-16872` | `13:41:48 Commencing graceful shutdown` · 502 8건 13:41:51 ~ 59 |

## 6. 수정안 (지금 적용)

| # | 대상 | 무엇을 · 어떻게 |
|---|---|---|
| 6.1 | 운영 DB (데이터) | readonly 계정으로 고아 배포를 찾는다: `SELECT d.id, d.class_id, d.assignment_id FROM distribution d LEFT JOIN assignment a ON a.id = d.assignment_id WHERE a.id IS NULL;` 결과 행은 담당자 결정에 따라 처리한다(assignment 9 복구, 또는 해당 배포 정리). 이 조치만으로 41 · 42 · 43 의 500 이 모두 멎는다. |
| 6.2 | 운영 DB (데이터) | distribution 42 · 43 의 `redistributed` · `distributed_at` 를 확인한다. 500 을 받고 재시도한 39건이 중복 반영됐는지 보고, 필요하면 바로잡는다. |
| 6.3 | `DistributionController.java:33-42` · `DistributionService.java` | 재배포와 이력 조회를 서비스 메서드 하나(예: `redistributeWithHistory(id, reason)`)로 합쳐 `@Transactional` 하나로 묶는다. 컨트롤러는 그 메서드만 부른다. 이력 조회가 실패하면 재배포도 롤백돼 "500 인데 반영됨" 상태가 생기지 않는다. 예외는 지금처럼 던져 `GlobalExceptionHandler` 가 처리한다(try/catch 로 삼키지 않음). |
| 6.4 | 배포 | 6.1 을 바로 할 수 없으면 `deploy-history.md:22` 의 롤백 계획대로 `v1.4.1` 로 되돌린다. 학급 1 재배포는 복구되지만 41 직접 조회 500 은 남는다. |

## 7. 재발 방지 (구조적 장치)

| # | 장치 | 막는 것 |
|---|---|---|
| 7.1 | 운영 스키마와 `db/mariadb/init/01-schema.sql` 을 정기적으로 비교한다(스키마 덤프 diff). FK 가 빠져 있으면 실패로 처리하고, FK 를 다시 건다. | 고아 참조 데이터 생성 |
| 7.2 | 참조 무결성 점검 쿼리(6.1 의 LEFT JOIN 형태, 주요 FK 전부)를 매일 배치로 돌려 0건이 아니면 티켓을 만든다. | 고아 데이터가 쌓인 채로 방치 |
| 7.3 | `@DataJpaTest` 와 스테이징 데이터에 "참조 대상이 없는 배포" 같은 엣지 픽스처를 넣는다. 또는 익명화한 운영 스냅샷으로 검증한다. | 시드로만 검증해 놓치기 |
| 7.4 | `templates/approval-checklist.md` 에 두 항목을 넣는다. "응답에 목록 조회를 붙이면 그 목록 전체 데이터에 의존하게 된다 — 한 행 실패 시 동작을 확인했는가", "쓰기 API 는 서비스 메서드 하나 · 트랜잭션 하나인가". | 단건 API 가 학급 전체 데이터에 묶이기 · 반쪽 커밋 |
| 7.5 | 배포 전 게이트: 변경 대상 API 에 최근 24시간 동안 5xx 가 있으면 먼저 해결하거나 승인자가 명시적으로 수용한다. | 이미 알려진 오류 위에 배포하기 |
| 7.6 | 대기 호스트 `api-2` 로 무중단(교대) 배포를 하고, 수업 시간대 배포는 막는다. 배포 후 8절 M3 이 울리면 자동으로 롤백한다. | 재기동 502 · 장애 지속 시간 |

## 8. 모니터링 항목

이번 로그에 대입한 경보 시각은 nginx `500` 기준이다(502 제외, 1.3 · 3.1 의 집계).

| # | 지표 | 임계값 | 이번에 울렸을 시각 (KST) |
|---|---|---|---|
| M1 | app ERROR 중 참조 무결성 예외(`FetchNotFoundException` · `EntityNotFoundException` · `JpaObjectRetrievalFailureException`) 건수 | 1건 이상 즉시 | **09:52:17** — 배포 3시간 48분 전. 배포 후에는 13:46:38 |
| M2 | API(경로 패턴)별 5xx 건수 | 같은 API 5분 합 3건 이상 | 09:52(재배포 3건) · 11:18 · **13:47**(배포 후, 첫 500 뒤 1분 이내) |
| M3 | 배포 직후 30분 동안의 5xx (배포 이벤트 기준) | 배포 완료 후 10분 안에 `500` 3건 이상, 또는 직전 1시간 평균의 5배 이상 | **13:47** (배포 완료 13:42 + 5분, 13:46 2건 + 13:47 1건) → 7.6 자동 롤백과 연결 |
| M4 | 배포 중 5xx(502 · 503) 건수 | 배포 1회당 1건 이상이면 정보 알림(장애 경보는 아님) | 13:41:51 (8건) |
| M5 | 재배포 API 성공률 | 10분 창 성공률 80% 미만(요청 3건 이상) | 13:50 (13:46 ~ 13:50 재배포 8건 중 7건 500) |

이번 장애는 시작(13:46:38)부터 로그 끝까지 경보 없이 53분 이상 이어졌다. M1 이 있었다면 배포 전에 고아 데이터를 발견했을 것이다.

## 9. 확인하지 못한 것

| 판단하지 못한 것 | 이유 | 다음을 위해 남길 로그 · 자료 |
|---|---|---|
| 고아 배포가 distribution 41 이고 학급 1 소속인가 (H2) | 41 은 로드 단계에서 실패해 class 가 로그에 없다 | 운영 DB 조회(6.1). 앱 로그에 요청 대상 id · classId 를 MDC 로 남기기 |
| assignment 9 가 언제 · 어떻게 사라졌나, 운영에 FK 가 있나 | 로그가 09:00 부터라 그 이전이 없다. DB 로그가 없다 | DB binlog · 감사 로그 보존, 스키마 덤프 |
| 500 을 받은 재배포 39건이 DB 에 반영됐나 (H5) | `redistributed` INFO 는 커밋 전에 찍힌다 | 쓰기 API 감사 로그(커밋 후 기록), DB 변경 이력 |
| 장애가 언제 끝났나 (H7), 롤백했나 | 로그가 14:39:58 에서 끝나고 v1.4.2 이후 배포 기록이 없다 | 14:40 이후 app · nginx 로그, 롤백 · 핫픽스 기록 |
| 영향받은 사용자 수 | 500 요청이 IP 1개에서 왔지만 NAT 일 수 있다 | nginx 에 인증 사용자 ID(가명화) 또는 요청 ID |
| 응답 지연 여부 | nginx 에 응답 시간 필드가 없다 | nginx `$request_time` · `$upstream_response_time`, `X-Request-Id` 로 app 로그와 연결 |

---

마스킹 적용: IP 1건(역할 별칭 `교사단말A` — 배포 전후 500 을 받은 재배포 요청 클라이언트) · 이메일 0건 · 학생 식별자 0건 · 토큰 1건(`[TOKEN]`) · 비밀번호 · 접속 문자열 0건. 규칙: IP 는 앞 두 옥텟만 남기거나 역할 별칭, 이메일은 `***@도메인`, 학생은 등장 순 `학생A`…, 토큰 · 세션 · 키 · Authorization 값은 `[TOKEN]`, 비밀번호 · 접속 문자열은 `[SECRET]`.

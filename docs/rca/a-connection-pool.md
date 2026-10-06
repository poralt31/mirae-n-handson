# a-connection-pool 장애 분석

대상: `incident-logs/a-connection-pool/` (2026-09-16 하루치 로그 4개)

## 1. 수집 범위

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 (예) | 타임존 |
|---|---:|---|---|---|
| `app.log` | 5,371 (타임스탬프 있는 줄 1,927 · 스택 트레이스 연속 줄 3,444) | 06:43:56 ~ 15:29:51 | ISO 8601 + 오프셋 `2026-09-16T14:37:52.199+09:00` | KST (`+09:00` 명시) |
| `nginx-access.log` | 21,086 | 06:43:56 ~ 15:29:59 | combined `[16/Sep/2026:14:40:52 +0900]` | KST (`+0900` 명시) |
| `nginx-error.log` | 168 | 09:12:03 ~ 14:51:47 (이벤트가 있을 때만 기록) | `2026/09/16 14:41:06` | 표기 없음 → **KST 로 확인** (아래 근거) |
| `mariadb-slow.log` | 21 (항목 3건) | 쿼리 시작 09:14:07 · 11:02:40 · 14:37:42 (연속 기록이 아니라 3개 시점) | `# Time: 260916  5:37:52` (YYMMDD, 완료 시각) + `SET timestamp=1789537062` (epoch, 시작 시각) | `# Time` 은 **UTC**, `SET timestamp` 는 epoch |

**KST 변환 규칙**

- `app.log`, `nginx-access.log`: 이미 KST. 변환 불필요.
- `nginx-error.log`: 그대로 KST 로 읽는다. 근거
  - 14:51:47 의 `upstream timed out` 줄과 access 로그의 14:51:47 `504` 가 같은 요청(`GET /api/classes/3/report`, 같은 클라이언트)이다.
  - 건수가 정확히 맞는다: `upstream timed out` 55건 = access `504` 55건, `no live upstreams` 68건 + `connect() failed (111)` 14건 = 82건 = access `502` 82건.
- `mariadb-slow.log`: `SET timestamp=<epoch>` 를 기준으로 `TZ=Asia/Seoul date -d @<epoch>` 로 바꾼다. 이것이 쿼리 **시작** 시각이다.
  `# Time` 은 UTC 기준 **완료** 시각이므로 KST = `# Time` + 9시간이고, `epoch + Query_time` 과 일치한다.
  검산: `1789537062` → 14:37:42 KST, + Query_time 9.8초 = 14:37:52 KST = `# Time 260916 5:37:52`(UTC) + 9h.
  날짜가 바뀌는 경우 주의: UTC 15:00 이후는 KST 다음 날이다(이번 로그에는 해당 없음).

### 1.2 이상 신호로 센 줄의 정의

| 파일 | 센 것 | 제외한 것 (평소에도 꾸준히 나오는 배경) |
|---|---|---|
| `app.log` | 레벨이 `WARN` · `ERROR` 인 타임스탬프 줄 | `state conflict ... 재배포할 수 없습니다`(WARN, 409) — 08:41 ~ 14:52 동안 시간당 1건 안팎으로 고르게 나옴. `INFO` 의 not found · no handler 등 |
| `nginx-access.log` | 상태 코드 `5xx` (500 · 502 · 504) | `404`(시간당 약 150건), `400`, `409` — 시간대별로 일정 |
| `nginx-error.log` | `[error]` · `[warn]` | `[info]` 3건(긴 헤더 · keepalive 종료) |
| `mariadb-slow.log` | 항목 전부 (3건) | — |

건수 정합성(교차 확인):
- `app.log` WARN 203 = 커넥션 누수 감지 134 + `SQL Error` 49 + `state conflict` 20
- `app.log` ERROR 98 = 풀 대기 타임아웃(`Connection is not available, request timed out after 3000ms`) 49 + `unhandled exception` 49
- access `500` 49건 = app `unhandled exception` 49건

### 1.3 분 단위 건수 (14:37 ~ 14:52, KST)

이 구간 밖의 평소 수준:
- `app.log` WARN/ERROR(배경 제외): 06:43 ~ 14:36 **0건**, 14:52 ~ 15:29 **0건**
- `nginx-access.log` 5xx: 14:40:52 이전 **0건**, 14:51:47 이후 **0건**
- `nginx-error.log`: 09:12 · 13:05 에 `[warn]`(upstream 응답 임시 파일 버퍼링) 1건씩만 있음
- 요청량: 08:20 이후 10분당 약 350 ~ 500건(분당 40 ~ 50건)으로 평탄. 장애 구간에도 분당 34 ~ 58건으로 **요청량 급증은 없음**

| 분 | 요청 수 (access) | app WARN/ERROR | └ 누수 감지 WARN | └ 풀 대기 타임아웃 ERROR | access 500 | access 502 | access 504 | nginx-error error+warn | slow log |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 14:37 | 50 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 시작 14:37:42 · 완료 14:37:52 (9.8초) |
| 14:38 | 41 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | |
| 14:39 | 42 | 3 | 3 | 0 | 0 | 0 | 0 | 0 | |
| 14:40 | 42 | 17 | 14 | 1 | 1 | 0 | 0 | 0 | |
| 14:41 | 42 | 31 | 13 | 6 | 6 | 0 | 3 | 3 | |
| 14:42 | 41 | 16 | 10 | 2 | 2 | 13 | 5 | 20 | |
| 14:43 | 58 | 44 | 14 | 10 | 10 | 2 | 5 | 8 | |
| 14:44 | 34 | 17 | 11 | 2 | 2 | 5 | 6 | 15 | |
| 14:45 | 39 | 33 | 9 | 8 | 8 | 14 | 5 | 22 | |
| 14:46 | 51 | 24 | 12 | 4 | 4 | 16 | 6 | 25 | |
| 14:47 | 38 | 24 | 9 | 5 | 5 | 6 | 3 | 10 | |
| 14:48 | 42 | 22 | 10 | 4 | 4 | 6 | 5 | 15 | |
| 14:49 | 42 | 33 | 12 | 7 | 7 | 8 | 8 | 20 | |
| 14:50 | 34 | 11 | 11 | 0 | 0 | 11 | 7 | 22 | |
| 14:51 | 46 | 3 | 3 | 0 | 0 | 1 | 2 | 3 | |
| 14:52 | 47 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | |
| **합계** | | **281** | **134** | **49** | **49** | **82** | **55** | **163** | |

- app WARN/ERROR 에는 위 두 열 외에 `SQL Error`(WARN) · `unhandled exception`(ERROR)이 풀 대기 타임아웃과 1:1 로 같이 찍힌다.
- nginx-error 종류별 첫 · 마지막 시각: `upstream timed out` 14:41:06 ~ 14:51:47, `upstream server temporarily disabled` 14:42:01 ~ 14:50:52, `connect() failed (111: Connection refused)` 14:42:01 ~, `no live upstreams` 14:42:04 ~ 14:51:02.
- slow log 의 나머지 2건(시작 09:14:07 2.3초, 11:02:40 2.0초)은 앞뒤로 다른 이상 신호가 없다.

### 1.4 이상 구간 제안

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 가장 이른 이상 신호 | **14:37:42** | slow log 9.8초 쿼리 시작(완료 14:37:52), 같은 14:37:52 에 `app.log` 첫 커넥션 누수 감지 WARN. 누수 감지는 커넥션을 빌린 뒤 임계 시간(설정상 10초)이 지나야 찍히므로 실제 대여는 약 10초 앞이다 |
| 사용자 영향 시작 | **14:40:52** | 첫 풀 대기 타임아웃 ERROR 와 첫 access `500` 이 같은 초 |
| 사용자 영향 끝 | **14:51:47** | 마지막 access `504` 및 마지막 nginx-error 줄. 마지막 app WARN 은 14:51:37 |
| 평소 수준 복귀 | **14:52** 부터 | 5xx · app WARN/ERROR · nginx-error 모두 0, app 로그 분당 줄 수 1 ~ 6 으로 장애 전(14:20 ~ 14:36) 수준 |

→ 분석 핵심 구간: **14:37:42 ~ 14:51:47 KST** (UTC 05:37:42 ~ 05:51:47)

### 1.5 앞뒤 여유

| | 여유 | 수집 구간 (KST) | 이유 |
|---|---|---|---|
| 앞 | **30분** | **14:07:00** ~ | 전조(서서히 늘어나는 지연 · 특정 요청 패턴)는 아직 모르는 영역이라 넉넉히 둔다. 누수 감지 임계(10초) · 풀 대기(3초) · nginx 타임아웃(값 미확인)보다 훨씬 길고, 평소 요청 흐름을 1,300건 이상 비교 표본으로 확보한다 |
| 뒤 | **10분** | ~ **15:02:00** | 복귀는 14:52 부터 이미 로그에 보인다. 재발이 없는지와 복귀 후 평소 수준만 확인하면 된다 |

- 최종 수집 범위: **2026-09-16 14:07:00 ~ 15:02:00 KST** (UTC 05:07:00 ~ 06:02:00)
- `mariadb-slow.log` 는 UTC 로 기록되므로 같은 범위를 `# Time: 260916  5:07:00 ~ 6:02:00` 또는 epoch `1789535220 ~ 1789538520` 으로 자른다.
- 범위 밖 참고 표본: slow log 09:14 · 11:02 두 건은 비교용으로만 남긴다.

### 1.6 다음 단계 작업 시 주의

- 원본 로그에 IP(`10.20.x.x`), 학생 ID(`STU-xxxx`, access 쿼리 문자열), 이메일(app 재배포 사유), Bearer 토큰(nginx-error)이 들어 있다. 이 문서에 줄을 인용할 때는 가린다.
- `app.log` 는 스택 트레이스 연속 줄이 많아 본문 문자열로 grep 하면 타임스탬프 없는 줄이 섞인다. 분 단위 집계는 `^2026` 으로 시작하는 줄만 쓴다.

### 집계 방법 (재현용)

```bash
cd incident-logs/a-connection-pool
# app.log: 배경(409) 제외 WARN/ERROR 분 단위
grep -E '^2026\S+ +(WARN|ERROR)' app.log | grep -v 'state conflict' | cut -c12-16 | uniq -c
# app.log: 종류별 (타임스탬프 줄만)
grep -E '^2026' app.log | grep 'Connection leak detection' | cut -c12-16 | uniq -c
# nginx-access.log: 5xx 분 · 코드별
awk '$9>=500{print substr($4,14,5), $9}' nginx-access.log | sort | uniq -c
# nginx-access.log: 분당 요청 수
awk '{print substr($4,14,5)}' nginx-access.log | uniq -c
# nginx-error.log: error/warn 분 단위
grep -E '\[(error|warn)\]' nginx-error.log | awk '{print substr($2,1,5)}' | sort | uniq -c
# mariadb-slow.log: 시작 시각 KST 변환
for t in $(grep -o 'timestamp=[0-9]*' mariadb-slow.log | cut -d= -f2); do TZ=Asia/Seoul date -d @$t '+%F %T %Z'; done
```

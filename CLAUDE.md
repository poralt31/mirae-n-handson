# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 이 저장소

Claude Code 심화 과정(4회차) **실습 저장소**다. 코드 · 데이터 · 로그는 모두 더미 에듀테크 도메인이다.
뼈대는 "레거시 3모듈 → 현행(`modern/`) 이관"이고, 나머지 최상위 폴더는 회차별 실습 입력 자료다.
참가자가 포크해서 쓰고(`origin` = 내 포크, `upstream` = 원본), 보강 자료는 `git fetch upstream && git merge --no-edit upstream/main` 으로 받는다.
Claude Code 는 항상 저장소 루트에서 실행한다.

도메인 용어: 문항 `item` · 단원 `unit`(코드 `M5-1` 형식) · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

## 명령

```bash
# 인프라 — up 뿐 아니라 down · build · logs 에도 프로필을 붙인다
docker compose --profile php up -d          # 문항 은행 8081 + MariaDB 3306
docker compose --profile thymeleaf up -d    # 과제 배포 8082 + MariaDB
docker compose --profile mssql up -d        # 성적 집계 8083 + MS-SQL 1433 (Apple Silicon 에서는 느리거나 안 뜸)
docker compose --profile modern up -d       # MariaDB 만 (modern/api · web 은 로컬 실행)
docker compose --profile php down           # 영구 볼륨이 없다 → down 후 up 하면 시드 상태로 초기화
bash scripts/check-env.sh                   # 환경 점검

# modern/api (Spring Boot 3.3 · Java 21 · Gradle)
cd modern/api && ./gradlew test                                                   # H2(MariaDB 모드)로 돈다. DB 컨테이너 불필요
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest'        # 클래스 하나
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest.listUnitsEmpty'   # 메서드 하나
cd modern/api && ./gradlew bootRun                                                # 8080, modern 프로필 DB 필요

# modern/web (React 18 · TS · Vite)
cd modern/web && npm ci && npm run dev                                            # 5173
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/components/ItemTable.test.tsx                 # 파일 하나 (-t '<이름>' 으로 케이스 하나)

# characterization (동작 보존 테스트, Vitest)
cd characterization && npm ci
cd characterization && npm run baseline -- <item-bank|assignment|grade>           # 스냅샷 새로 찍기
cd characterization && npm test                                                   # 레거시 기본 포트 대상
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test             # 같은 테스트를 새 API 대상으로

# mcp-skeleton (TypeScript MCP 서버 골격)
cd mcp-skeleton && npm ci && npm run build && npm start
```

의존성은 항상 `npm ci` 로 설치한다. `npm install` 은 `package-lock.json` 을 바꿔 버린다.

## 아키텍처 — 여러 파일을 봐야 보이는 것

### 모듈과 DB
| 모듈 | 스택 | 비즈니스 로직 위치 |
|---|---|---|
| `legacy/item-bank-php` | PHP 7.4 + mysqli | 페이지 파일 안의 절차형 함수(`search.php` 의 `buildSearchQuery` 등) |
| `legacy/assignment-thymeleaf` | Spring MVC + Thymeleaf + JDBC DAO | `service/DistributionService.java` (이전 버전 코드를 남긴 채 새 버전 코드를 덧붙인 구조) |
| `legacy/grade-mssql` | MS-SQL 저장 프로시저 + 얇은 Java 호출부 | `sql/usp_*.sql` |
| `modern/api` | Spring Boot 3 · JPA | 도메인 패키지 `item/`, `assignment/`, 공통 `common/`, 설정 `config/` |
| `modern/web` | React 18 · Vite | `src/api` → `src/hooks` → `src/components` |

- `php` · `thymeleaf` · `modern` 프로필은 **같은 MariaDB(`itembank`) 하나**를 공유한다. 스키마 · 시드는 `db/mariadb/init/*.sql`. 성적 집계만 별도 MS-SQL(`grades`, `db/mssql/init/*.sql`).
- `legacy/grade-mssql/sql/usp_*.sql` 과 `db/mssql/init/04-procs.sql` 은 **본문이 같아야 하는 사본**이다(Docker 빌드 컨텍스트가 `db/mssql` 이라서). 한쪽을 고치면 다른 쪽도 고친다.
- 시드는 고정 ID · 고정 시각이다. 시각에 의존하는 코드는 시계를 주입받는다(레거시 `AppClock`, 현행 `common/ClockConfig` 의 `Clock` 빈).
- 조회 실습용 계정은 SELECT 권한만 있다: MariaDB `readonly`/`readonly-pass`, MS-SQL `readonly`/`Readonly-pass1`. 쓰기 계정은 `docker-compose.yml` 안에서만 쓴다.

### 레거시 → 현행 이관 흐름 (`characterization/`)
1. 레거시 동작을 `characterization/tests/<모듈명>.test.js` 로 스냅샷에 고정한다. 모듈명은 폴더명이 아니라 `item-bank` · `assignment` · `grade`. 스냅샷(`__snapshots__/`)은 커밋하며 이것이 기준이다. 기대값을 손으로 쓰지 않는다.
2. `lib/normalize.mjs` 가 레거시 HTML(`<table id="items">`, `<p id="count">`, `<p id="message">`)과 새 API JSON(`{items, count, message}` 또는 배열)을 같은 모양 `{status, rows, count, message}` 로 바꾼다. 시각 · 세션 · 요청 ID 같은 가변 필드(`VOLATILE_KEYS`)는 지운다. 행 순서와 tags 순서는 **보존**한다 — 정렬 차이도 동작 차이다.
3. 대상 주소는 `lib/target.mjs` 한 곳에만 있고, 테스트는 `fetchNormalized(모듈, 레거시경로, 파라미터)` 만 쓴다. 새 API 에서 경로가 바뀌면 `PATH_ALIASES` 에 한 줄 추가한다(레거시 경로가 404 면 새 경로로 재요청). 테스트 코드 · 스냅샷은 고치지 않는다.
4. 같은 테스트를 `TARGET_BASE_URL=http://localhost:8080` 으로 돌려 스냅샷이 그대로 맞는지 본다.

### modern/api 규칙 (코드에 이미 깔린 것)
- Controller → Service → Repository. 컨트롤러는 서비스만 부르고, 엔티티 대신 record DTO(`XxxResponse.from(entity)`)를 돌려준다.

- DTO 생성시 테이블 구조에 맞게 Entity 나눠 구조화된 DTO 생성해주고, Lombok library로 관리해줘
- **OSIV 꺼짐**(`open-in-view: false`). 서비스는 `@Transactional(readOnly = true)` 안에서 DTO 로 바꿔 반환하고, 연관은 `@EntityGraph` 로 미리 로드한다.
- Hikari 풀이 운영 값과 같게 작다(최대 5, 대기 3초, 누수 감지 10초). 트랜잭션 안에서 오래 걸리는 작업을 하면 풀이 금방 고갈된다.
- 외부에 노출하는 문항은 `status = 'A'`(`ItemStatus.ACTIVE`)만. 삭제는 플래그가 아니라 상태 코드 `D`, 검수 중은 `R`.
- 예외 → HTTP 변환은 `common/GlobalExceptionHandler` 한 곳(`NotFoundException` → 404, `IllegalArgumentException`·검증 실패·타입 불일치 → 400, `IllegalStateException` → 409). 서비스 · 컨트롤러는 try/catch 로 삼키지 않고 던진다.
- CORS 는 `config/WebConfig` 에서 `http://localhost:5173` 의 `/api/**` **GET 만** 허용한다.
- 테스트: 컨트롤러는 `@WebMvcTest` + `@MockBean`, 서비스는 Mockito, 쿼리는 `@DataJpaTest`(H2, `application-test.yml`, `@ActiveProfiles("test")`). 픽스처는 `ItemFixtures`. 이름은 camelCase 메서드 + 한국어 `@DisplayName`.

### modern/web
- 모든 요청은 `src/api/client.ts` 의 `getJson` 을 거치고, 2xx 가 아니면 `ApiError` 를 던진다. 응답 타입은 `src/api/types.ts`.
- API 주소는 `VITE_API_BASE`(기본 `http://localhost:8080`)로 직접 호출한다. `VITE_API_BASE=""` 로 두면 Vite 프록시(`/api` → 8080)를 탄다.
- 데이터 로딩은 `src/hooks/useApiQuery.ts` 기반 훅(`useUnits`, `useUnitItems`, `useItem`)으로 하고, 로딩 · 오류 · 빈 결과 표시는 `AsyncSection` 컴포넌트가 맡는다.
- 테스트는 jsdom + `src/test/mockFetch.ts` · `fixtures.ts`.

## 완료 기준 — 이관 · 리팩토링
1. `characterization/` 의 `npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
2. 테스트가 실패하면 실패한 케이스와 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
3. 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
4. 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.

## 실습 재료 폴더 — 원본은 고치지 않는다
- `legacy/`: 분석 · 이관 **대상**이다. 요청 없이 수정하지 않는다.
- `incident-logs/`: 장애 로그 원본(파일에 따라 수만 줄). 통째로 읽지 말고 `wc -l` · `grep -n` · `sed -n` 으로 필요한 부분만 본다. 파일마다 시각 형식이 다르다. 분석 결과는 `docs/rca/<폴더명>.md` 에 쓰고, IP · 이메일 · 학생 ID · 토큰처럼 보이는 값은 가린다.
- `vendor-prs/*.patch`: 외주 PR 검증 자료(`git format-patch` 형식). `Subject:` 줄이 요청 범위다. `git apply --stat/--check` 로 보고, 적용은 `review/pr-N` 브랜치에서만 한다. 판정 기준은 `templates/verification-loop.md` · `templates/approval-checklist.md`.
- `pipeline-samples/`(배치 로그는 파일명이 기준일, 실행은 다음날 새벽), `ci-ports/`(`.gitlab-ci.yml` 은 숨김 파일), `specs/`(신규 개발 스펙 + `starters/java` · `starters/python` 골격)도 입력 자료다.
- `templates/CLAUDE.*.md` 는 스택별 CLAUDE.md 템플릿이다. 실제 코드와 다른 부분이 있으면 코드가 우선이다.
- Hook 은 `bash "$CLAUDE_PROJECT_DIR"/scripts/hook-node.sh <스크립트>` 로 실행한다. node 를 못 찾으면 명령을 막는다(fail-closed).

## 환경
기준 환경은 Windows + WSL2(Ubuntu 24.04) bash 이고 macOS(zsh)도 지원한다. 저장소는 WSL 홈(`~/work/mirae-n-handson`)에 두고 `/mnt/c` 는 쓰지 않는다. 필요한 도구: JDK 21, Node.js 22.5 이상, Python 3.12, Docker Compose, `gh`.

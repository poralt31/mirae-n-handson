# 실전 프로젝트 설계 — 백엔드 / 이승욱

## 1. 목표
레거시 과제 목록 화면 `GET /assignments`(assignment-thymeleaf) 하나를 동작 그대로 `modern/api` 로 옮기고, 동작 보존 테스트 통과와 PR 자동 리뷰 코멘트 1회로 완료를 증명한다.

> 범위 칸("최소 범위로 줄인다면")이 비어 있어서 **엔드포인트 1개 이관**으로 가정했다. → 4. 미해결 질문 Q1

## 2. 재료
| 재료 | 있음/없음 | 경로 |
|---|---|---|
| 이관 대상 레거시 코드 (과제 목록) | 있음 | `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/AssignmentController.java:26`, `.../dao/AssignmentDao.java:21-30`(`status <> 'D'`, `ORDER BY due_at, id`), `.../templates/assignments.html:8-21`(`<p id="count">`, `<table id="assignments">`) |
| modern 쪽 과제 목록 API | 없음 → 새로 만들 것 | `modern/api/src/main/java/com/example/assignment/` (엔티티 `Assignment.java` · `AssignmentRepository.java` 는 있음, 목록 컨트롤러 · 서비스 · DTO 없음) |
| 동작 보존 테스트 틀 | 있음 | `characterization/lib/target.mjs`(`PATH_ALIASES`), `characterization/lib/normalize.mjs`, `characterization/scripts/baseline.mjs` |
| `assignment` 모듈 동작 보존 테스트 · 스냅샷 | 없음 → 새로 만들 것 | `characterization/tests/assignment.test.js`, `characterization/__snapshots__/assignment.test.js.snap` (현재는 `item-bank` · `example-units` 만 있음) |
| 이관 참고 사례 (item-bank 검색, Day 1) | 있음 | `modern/api/src/main/java/com/example/item/ItemSearch*.java`, `characterization/tests/item-bank.test.js`, `docs/verify/migration-review-3.md` |
| 검증 Skill · Sub-agent | 있음 | `.claude/skills/verify/`, `.claude/skills/convention-check/`, `.claude/skills/impact/`, `.claude/agents/reviewer.md`, `.claude/agents/tester.md` |
| Hook | 있음 | `.claude/settings.json`(PreToolUse → `hooks/block-dangerous.mjs`), `hooks/secret-scan.mjs`, `scripts/hook-node.sh` |
| 판정 기준 | 있음 | `templates/approval-checklist.md`, `templates/verification-loop.md` |
| PR 자동 리뷰 설정 (GitHub Actions 등) | 없음 → 새로 만들 것 | `.github/` 폴더 자체가 없음 |
| 3회차 PR | 있음(단, 머지됨) | https://github.com/poralt31/mirae-n-handson/pull/1 (`practice/bad-pr` → `main`, MERGED, 코멘트 0건) |
| pipeline-samples | 백엔드 트랙에는 쓰지 않음 | `pipeline-samples/` |

## 3. 범위
### 한다
1. `GET /assignments` 기본 조회 케이스 1개로 레거시(8082) 스냅샷을 찍고, `modern/api` 에 `GET /api/assignments` 와 `PATH_ALIASES` 한 줄을 넣어 같은 테스트를 8080 대상으로 통과시킨다.
2. 변경분으로 PR 을 열고 자동 리뷰 코멘트 1회를 받는다.

### 하지 않는다
- 배포 등록 · 재배포(`POST /distributions…`) 이관 — 쓰기 동작이라 40분 안에 검증까지 끝낼 수 없다.
- `modern/web` 화면 추가 — 완성 기준이 API 동작 보존이라 화면은 기준에 들어가지 않는다.
- 레거시 동작의 버그 수정(예: 마감 지난 행 `past` 표시 조건) — CLAUDE.md 완료 기준 4, 이관 중에는 고치지 않고 "의심 동작"으로만 적는다.
- 성적 집계(MS-SQL) 모듈 — 별도 DB · 컨테이너가 필요하고 Apple Silicon 등에서 불안정하다.
- 기존 스냅샷 · 테스트 수정 — CLAUDE.md 완료 기준 3.
- 기본 조회 외 추가 케이스(빈 결과 · 상태별 등) — 완성 기준은 케이스 수가 아니라 "실패 0 · 스냅샷 일치"라서 1케이스로도 판정된다.
- `modern/api` 신규 단위 테스트(`@WebMvcTest` · Mockito · `@DataJpaTest`) — 완성 기준은 기존 `./gradlew test` 실패 0 이고, 이관 동작은 characterization 스냅샷 일치로 증명된다.
- GitHub Actions 리뷰 워크플로(`.github/workflows/`) 신규 작성 — 완성 기준은 PR 코멘트 1개 이상이고, 로컬 `/code-review --comment` 한 번으로도 같은 `gh pr view` 확인을 통과한다(수단 확정은 Q3).

## 4. 미해결 질문
- Q1. 범위 칸("최소 범위로 줄인다면")이 비어 있다. "엔드포인트 1개(`GET /assignments`) 이관"으로 가정했는데 맞는가?
- Q2. 3회차 PR #1 은 이미 **머지**됐다. 자동 리뷰 코멘트를 이 PR 에 받는 것인가, 이번 이관으로 새 PR 을 열어 받는 것인가?
- Q3. "PR 자동 리뷰"의 수단이 정해지지 않았다. GitHub Actions(`anthropics/claude-code-action`) 인지, 로컬에서 `/code-review --comment` 또는 `/verify` 결과를 `gh pr comment` 로 다는 것인지 확인이 필요하다. Actions 라면 저장소 Secrets(API 키) 등록 권한이 있는지도 미확인.
- Q4. 코드를 읽어 확인한 사실: `normalize.mjs:104-111` `headerToKey` 는 `assignments.html:12-17` 머리글을 `번호→id`, `과제명→col2`, `단원→unit`, `마감→col4`, `상태→status`, `배포 학급 수→col6` 으로 바꾼다(`HEADER_MAP` 에 과제명 · 마감 · 배포 학급 수가 없음). 건수는 `parseCount` 가 "과제 N건" 에서 숫자를 뽑는다(`normalize.mjs:99`, `:173`). 값은 화면 표시 문자열 그대로다 — 마감은 `yyyy-MM-dd HH:mm`, 상태는 `진행중` · `연장` · `종료` · `검수중`(`assignments.html:25-26`). **질문**: (a) modern API 가 JSON 필드명을 `col2` · `col4` · `col6` 으로 내보내 맞출 것인가, (b) 단계 1 스냅샷을 찍기 **전에** `normalize.mjs` 의 `HEADER_MAP` 에 세 줄(`과제명→title`, `마감→due_at`, `배포 학급 수→class_count`)을 추가할 것인가. (b)는 `characterization/lib/` 수정이라 CLAUDE.md 완료 기준 3(테스트 코드 수정 금지)에 걸리는지 판단이 필요하다. 기본안은 (b), 승인 전에는 진행하지 않는다.
- Q5. 레거시의 마감 지난 행 `past` 표시(`assignments.html:21`, `AppClock.now()` 기준)는 `<tr>` class 라서 `normalize.mjs` 가 읽지 않는다(셀 텍스트만 읽음) → 스냅샷에 남지 않는다. 이관 범위에서 이 표시를 빼도 되는지 확인 필요.
- Q6. MariaDB 컨테이너(`thymeleaf` · `modern` 프로필)가 이 PC 에서 뜨는지 오늘은 실행해 보지 않았다.

## 5. 단계

### 단계 1 — 스냅샷 → 이관 → 같은 테스트 통과 (약 30분)
- 할 일: ① 레거시 과제 목록을 띄우고 기본 조회 케이스 1개로 스냅샷을 찍어 **바로 커밋**한다(이 커밋이 기준). ② `GET /api/assignments` 를 Controller → Service(`@Transactional(readOnly = true)`) → Repository 로 만들고, 레거시와 같은 조건(`status <> 'D'`)과 정렬(`due_at ASC, id ASC`)을 지키고, 응답은 `{items, count}` 에 레거시 화면과 같은 표시 문자열(마감 `yyyy-MM-dd HH:mm`, 상태 한국어 라벨)과 Q4 에서 정한 필드명을 쓴다. ③ `PATH_ALIASES` 에 한 줄 넣고 같은 테스트를 8080 대상으로 돌린다.
- 만들거나 고칠 파일: `characterization/tests/assignment.test.js`(신규), `characterization/__snapshots__/assignment.test.js.snap`(신규, 자동 생성), `modern/api/src/main/java/com/example/assignment/AssignmentController.java`(신규), `AssignmentService.java`(신규), `AssignmentListResponse.java`(신규 DTO), `AssignmentRepository.java`(조회 메서드 추가), `characterization/lib/target.mjs`(`PATH_ALIASES` 에 `'/assignments': '/api/assignments'` 한 줄)
- 확인 방법:
  ```bash
  docker compose --profile thymeleaf up -d
  cd characterization && npm run baseline -- assignment && npm test   # 레거시 대상, 실패 0
  git add tests/assignment.test.js __snapshots__/assignment.test.js.snap && git commit -m "test: assignment 목록 동작 보존 기준"
  cd ../modern/api && ./gradlew test                                  # BUILD SUCCESSFUL, 실패 0
  ./gradlew bootRun                                                   # 별도 터미널, 8080
  cd ../../characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # assignment.test.js 스냅샷 일치
  git diff --stat HEAD -- tests __snapshots__                          # 기준 커밋 이후 변경 0줄
  ```
  눈으로: `.snap` 안에 `rows` 가 비어 있지 않고, `count` 가 숫자다(Q4).

### 단계 2 — PR 과 자동 리뷰 코멘트 (약 10분)
- 할 일: 작업 브랜치를 push 하고 PR 을 연 뒤, 자동 리뷰 코멘트 1회를 받는다(수단은 Q2 · Q3 답에 따름).
- 만들거나 고칠 파일: 없음
- 확인 방법:
  ```bash
  gh pr view <PR번호> --json url,comments --jq '{url, n: (.comments|length)}'   # n >= 1
  ```
  눈으로: PR 페이지에 리뷰 코멘트가 1개 이상 달려 있고, 작성자가 자동 리뷰(봇 또는 `/code-review` 결과)다.

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `cd characterization && npm test`(레거시 8082 대상) 실행 결과 실패 0건이다.
- [ ] `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` 실행 결과 `assignment.test.js` 의 모든 케이스가 스냅샷과 일치하고 실패 0건이다.
- [ ] 단계 1 커밋 이후 `git diff --stat -- characterization/tests characterization/__snapshots__` 출력이 비어 있다(테스트 · 스냅샷을 고쳐서 통과시키지 않았다).
- [ ] `cd modern/api && ./gradlew test` 가 BUILD SUCCESSFUL, 실패 0건이다.
- [ ] PR 주소(3회차 PR 또는 이번 PR, Q2) 에 자동 리뷰 코멘트가 1개 이상 있다 — `gh pr view <번호> --json comments --jq '.comments|length'` 결과가 1 이상.

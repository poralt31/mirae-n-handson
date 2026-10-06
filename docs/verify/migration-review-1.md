# 머지 전 검증 — migration-review-1

- 검증 대상: `upstream/main...HEAD` (브랜치 `day1`, 커밋 `f44db61` · `d7d1d32` · `e56dbf4`)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 기준: `templates/verification-loop.md`, `templates/approval-checklist.md`, `CLAUDE.md`, `.claude/skills/convention-check/SKILL.md` 점검 항목
- 리뷰 세션은 코드를 수정하지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | `cd modern/api && ./gradlew test --rerun` → 44건 통과, 실패 0, skip 0. **characterization 은 실행 불가**: 레거시 8081 · 새 API 8080 이 모두 응답 없음(연결 실패). `npm test`(레거시 대상) · `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API 대상) 미실행 → 사람이 기동한 뒤 재실행 필요. 이관 완료 기준(CLAUDE.md 완료 기준 1)이 확인되지 않았다. 이슈 #5 참고 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. SQL 은 Criteria API 파라미터 바인딩(`ItemSearchSpecs.java:35-70`), 비밀값 리터럴 없음, 데이터 삭제 · 덮어쓰기 코드 없음, 공개 문항만 조회(`ItemSearchRow.java:19` → 뷰 `v_item_public`, `db/mariadb/init/01-schema.sql:70` `status = 'A'`) |
| 요청 범위 이탈 없음 | 미충족 | 요청은 "엔드포인트 1개 이관"인데 범위에 `.claude/hooks/log-prompt.py` · `.claude/settings.json` · `.claude/skills/*` · `CLAUDE.md` · `docs/item-bank/*` 가 포함되어 있다(커밋 `d7d1d32`). 의존성 추가(`build.gradle` Lombok)도 요청 문장에 없다. 이관 코드 자체는 커밋 `e56dbf4`(modern/api 14개 파일)에 모여 있고, 동작 보존 베이스라인(`f44db61`)은 이관 절차의 일부로 볼 수 있다. 이슈 #1 · #2 · #3 |
| 컨벤션 준수 | 미충족 | CLAUDE.md 안의 DTO 규칙이 서로 충돌하고, 새 DTO 가 기존 record 관례와 다르다. 이슈 #4. convention-check 항목 1 · 3 · 4 · 5 · 7 은 통과(컨트롤러는 서비스만 참조, 서비스 클래스 `@Transactional(readOnly = true)`, catch 는 값을 돌려주며 삼키지 않음 `ItemSearchCondition.java:157`, `now()` 없음, 새 테스트 24건 모두 한국어 `@DisplayName`) |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `.claude/settings.json:7`, `.claude/settings.json:10` | 공유(커밋되는) 프로젝트 설정에 개인 홈 절대경로 `/home/leesu/.claude` 의 Read 권한과 `additionalDirectories` 가 들어 있다. 포크해서 쓰는 다른 참가자 환경에서는 경로가 맞지 않고, 개인 권한이 팀 설정으로 퍼진다. 요청 범위(이관)와도 무관하다 | 이 항목들을 `.claude/settings.local.json`(이미 `.gitignore:5` 에 있음)으로 옮기거나 이관 브랜치에서 뺀다 |
| 2 | 경고 | `.claude/hooks/log-prompt.py:10-13`, `.gitignore:5` | 사용자 프롬프트 전문을 `.claude/prompt-logs/<날짜>.txt` 에 쓰는데 이 폴더가 `.gitignore` 에 없다 → 프롬프트(붙여 넣은 로그 · 개인정보 포함 가능)가 `git add .` 로 커밋될 수 있다. 또 `.claude/settings.json` 에 hooks 등록이 없어 현재는 실행되지 않는 파일이다. 요청 범위와 무관하다 | `.gitignore` 에 `.claude/prompt-logs/` 를 추가하고, hook 을 쓸 거라면 CLAUDE.md 규칙대로 `scripts/hook-node.sh` 경유 등록을 별도 변경으로 분리한다. 아니면 이 브랜치에서 뺀다 |
| 3 | 경고 | `modern/api/build.gradle:26-27` | Lombok 의존성(`compileOnly` · `annotationProcessor`) 추가. 체크리스트 3번 "의존성 추가는 요청에 포함돼 있어야 한다"에 걸린다. 근거는 같은 브랜치에서 추가된 `CLAUDE.md:73` 뿐이고 요청 문장에는 없다 | 사람이 Lombok 도입을 승인했는지 확인한다. 승인하지 않았다면 DTO 를 기존 관례인 record 로 바꾸고 의존성을 뺀다 |
| 4 | 경고 | `CLAUDE.md:71`, `CLAUDE.md:73`, `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:13-16`, `modern/api/src/main/java/com/example/item/ItemSearchRowResponse.java:14-17` | CLAUDE.md:71 은 "record DTO(`XxxResponse.from(entity)`)", CLAUDE.md:73 은 "Lombok library로 관리" — 두 규칙이 충돌한다. 새 DTO 두 개는 Lombok `@Getter @Builder` 클래스이고, 기존 DTO 는 record(`UnitResponse.java:4`)다. 같은 패키지 안에 DTO 스타일이 두 가지가 된다. CLAUDE.md:73 은 AI 에게 하는 지시문 형태("관리해줘")라 팀 컨벤션 문장으로도 모호하다 | 팀이 하나로 정한다. record 로 정하면 CLAUDE.md:73 을 지우고 두 DTO 를 record 로 바꾼다. Lombok 으로 정하면 CLAUDE.md:71 의 "record" 를 고치고 적용 범위(신규 DTO 만인지)를 명시한다 |
| 5 | 경고 | `modern/api/src/test/java/com/example/item/ItemSearchRowRepositoryTest.java:18-19`, `:53` | H2 에서는 `v_item_public` 이 뷰가 아닌 테이블(`ddl-auto: create-drop`)이라 행을 직접 넣는다. 그래서 `status='A'` 필터, `tag_names` 의 태그 id 순 이어 붙이기, `utf8mb4_unicode_ci` 의 대소문자 무시(`unit=m5-1`) · 한글 제목 정렬은 단위 테스트로 검증되지 않는다. 이 부분은 characterization 에만 의존하는데 이번 검증에서 실행하지 못했다 | 새 API 대상 characterization(`TARGET_BASE_URL=http://localhost:8080 npm test`)을 반드시 돌려 17건 스냅샷 일치를 확인한다. 테스트 코드 · 스냅샷은 고치지 않는다 |
| 6 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchCondition.java:173-183` | `phpParam` 은 `name[]` 형태만 배열로 본다. PHP 는 `name[0]=` · `name[k]=` 도 배열로 받아 `reset()` 으로 첫 값을 쓴다(`legacy/item-bank-php/search.php:53-80`). `tag=a&tag[]=b` 처럼 두 형태가 섞일 때 PHP 는 뒤에 온 쪽이 이기지만 여기서는 항상 `name[]` 이 이긴다. 현재 스냅샷 케이스(`tag[]`)에는 영향이 없다 | 의심 동작 목록에 남겨 두고, 필요하면 `name[...]` 키 전반과 파라미터 순서를 반영한다 |
| 7 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchFixtures.java:7` | CLAUDE.md:79 "픽스처는 `ItemFixtures`" 인데 별도 픽스처 클래스를 새로 만들었다 | `ItemFixtures` 에 `searchRow(...)` 로 합친다 |
| 8 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchServiceTest.java:23-24` | Mockito 단위 테스트(`@ExtendWith(MockitoExtension.class)`)에 `@ActiveProfiles("test")` 가 붙어 있다. 스프링 컨텍스트를 띄우지 않으므로 효과가 없다 | 어노테이션을 지운다 |

## 확인 필요

- `characterization/__snapshots__/item-bank.test.js.snap` — convention-check 8번(스냅샷 변경) 대상이다. 신규 베이스라인 추가(`f44db61`)라 이관 절차 1단계로 보이지만, `npm run baseline -- item-bank` 로 레거시를 대상으로 찍은 것인지(손으로 쓴 기대값이 아닌지) 사람이 확인해야 한다.
- `modern/api/src/main/java/com/example/item/ItemSearchRowResponse.java:27-28` — 레거시 HTML 은 정규화 단계에서 제목의 연속 공백을 한 칸으로 줄이지만(`characterization/lib/normalize.mjs` `text()`), JSON 제목은 그대로 비교한다. 시드에 연속 공백이 있는 제목이 있으면 스냅샷이 어긋날 수 있다. characterization 실행 결과로 판단한다.
- `.claude/skills/convention-check` · `document-module` · `impact` SKILL.md, `docs/item-bank/*` — 내용 자체의 문제는 보지 않았다. 요청 범위 밖이라 별도 PR 로 분리할지 사람이 정한다.

## 사람에게 요청

characterization 을 돌리려면 다음 두 가지를 띄워야 한다.

1. 레거시: `docker compose --profile php up -d` (8081 + MariaDB)
2. 새 API: `cd modern/api && ./gradlew bootRun` (8080, MariaDB 를 같이 쓴다)

그다음 `cd characterization && npm test` 와 `TARGET_BASE_URL=http://localhost:8080 npm test` 를 실행하고, 통과 · 실패 수를 이 문서의 "테스트 통과" 칸에 반영한다.

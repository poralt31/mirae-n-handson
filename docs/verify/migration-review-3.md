# 머지 전 검증 — migration-review-3

- 검증 대상: `upstream/main...HEAD -- modern characterization` (브랜치 `day1`, 이 경로를 건드린 커밋은 `f44db61` · `e56dbf4` 둘뿐)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 기준: `templates/verification-loop.md`, `templates/approval-checklist.md`, `CLAUDE.md`, `convention-check` Skill 결과
- 리뷰 세션은 코드를 수정하지 않았다.
- **review-1 과의 관계**: review-1 이후 `modern/` · `characterization/` 코드는 한 줄도 바뀌지 않았다(`61a1113` "1차 리뷰 반영"은 이 경로를 건드리지 않음). 따라서 review-1 의 #3 ~ #8 은 모두 미반영 상태이고, review-1 의 #1 · #2(`.claude/*`)는 이번 pathspec 밖이라 판정에서 뺐다. 이번 문서는 재검증이라기보다 범위를 좁힌 재판정이며, review-1 때 실행하지 못한 새 API 대상 characterization 을 이번에 실행했다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | ① `cd modern/api && ./gradlew test --rerun` → 44건 통과, 실패 0, skip 0. ② `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` → 30건 통과, 실패 0, 1건 skip(이 변경과 무관한 기존 `characterization/tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)`). 그중 `item-bank.test.js` 17건 전부 스냅샷 일치. 8080 은 `modern/api/build/classes` 로 뜬 bootRun(PID 266619)이고 modern 작업 트리는 HEAD 와 같다. ③ `cd characterization && npm test`(레거시 대상) → **실행 불가**: 8081 연결 거부(ECONNREFUSED). 레거시 대상 결과가 없으므로 이 기준은 충족으로 올리지 않는다. 사람이 `docker compose --profile php up -d` 후 재실행해야 한다 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. SQL 은 Criteria API 파라미터 바인딩(`ItemSearchSpecs.java:37`, `:42`, `:57`, `:67`), 비밀값 리터럴 없음, 삭제 · 덮어쓰기 코드 없음, 공개 문항 뷰만 조회(`ItemSearchRow.java:19` → `db/mariadb/init/01-schema.sql:70` `status = 'A'`) |
| 요청 범위 이탈 없음 | 미충족 | 변경 16개 파일은 이관 코드(modern/api main 8 · test 5)와 동작 보존 베이스라인(characterization 2, 둘 다 신규 추가 `A`)으로 모두 "엔드포인트 1개 이관"으로 설명된다. 다만 의존성 추가(`modern/api/build.gradle:26-27` Lombok)가 요청 문장에 없다(체크리스트 3-4). 이슈 #1 |
| 컨벤션 준수 | 미충족 | DTO 규칙 충돌 — upstream 의 `CLAUDE.md:71` 은 record DTO, 같은 브랜치 커밋 `d7d1d32` 가 추가한 `CLAUDE.md:73` 은 Lombok. 새 DTO 는 Lombok 클래스라 기존 record 관례와 갈린다. 이슈 #2. convention-check 결과는 아래 표 |

### convention-check 결과 (`upstream/main...HEAD -- modern`, 대상 14개 파일)

| # | 항목 | 판정 |
|---|---|---|
| 1 | 컨트롤러가 Repository 를 직접 참조하지 않는다 | 통과 (`ItemController.java:18-19` 서비스 두 개만) |
| 2 | 컨트롤러가 엔티티 대신 DTO 를 반환한다 | 통과 (`ItemSearchResponse`). 단, record 가 아닌 Lombok 클래스 → 이슈 #2 |
| 3 | 서비스 조회가 `@Transactional(readOnly = true)` 안 | 통과 (`ItemSearchService.java:17`) |
| 4 | 예외를 삼키지 않음 · 변환은 `GlobalExceptionHandler` 한 곳 | 통과 (`ItemSearchCondition.java:157-158` 은 PHP 오버플로 의미를 옮긴 값 반환, 삼킴 아님) |
| 5 | 시각은 주입한 `Clock` | 통과 (`now()` 없음) |
| 6 | 웹 HTTP 호출은 `getJson` | 해당 없음 |
| 7 | 테스트 메서드에 한국어 `@DisplayName` | 통과 (변경 테스트 파일 `@Test` 24건 = `@DisplayName` 24건) |
| 8 | 원본 보존 | 통과 (`characterization/__snapshots__/item-bank.test.js.snap` 은 기존 스냅샷 수정이 아니라 베이스라인 신규 추가) |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/build.gradle:26-27` | Lombok 의존성(`compileOnly` · `annotationProcessor`) 추가. 체크리스트 3-4 "의존성 추가는 요청에 포함돼 있어야 한다"에 걸린다. 근거는 같은 브랜치에서 추가된 `CLAUDE.md:73` 뿐이고 요청 문장에는 없다. (review-1 #3, 미반영) | 사람이 Lombok 도입을 승인했는지 확인한다. 승인하지 않았다면 DTO 를 record 로 바꾸고 의존성을 뺀다 |
| 2 | 경고 | `CLAUDE.md:71`, `CLAUDE.md:73`, `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:13-16`, `modern/api/src/main/java/com/example/item/ItemSearchRowResponse.java:14-17` | `CLAUDE.md:71`(upstream 규칙)은 "record DTO", `CLAUDE.md:73`(커밋 `d7d1d32` 에서 추가)은 "Lombok library로 관리" — 규칙끼리 충돌한다. 새 DTO 두 개는 `@Getter @Builder` 클래스, 기존 DTO 는 record(`UnitResponse.java:4`, `ItemResponse.java:8`). 같은 패키지에 DTO 스타일이 두 가지가 된다. (review-1 #4, 미반영) | 팀이 하나로 정한다. record 로 정하면 `CLAUDE.md:73` 을 지우고 두 DTO 를 record 로 바꾼다. Lombok 으로 정하면 `CLAUDE.md:71` 을 고치고 적용 범위를 명시한다 |
| 3 | 경고 | `modern/api/src/test/java/com/example/item/ItemSearchRowRepositoryTest.java:51-63`, `modern/api/src/test/resources/application-test.yml:12` | H2 에서는 `v_item_public` 이 뷰가 아닌 테이블(`ddl-auto: create-drop`)이라 행을 직접 넣는다. `status='A'` 필터, `tag_names` 의 태그 id 순 연결, `utf8mb4_unicode_ci` 대소문자 무시 · 한글 정렬은 단위 테스트로 검증되지 않고 characterization 에만 의존한다. 이번에 새 API 대상 17건은 일치했지만 레거시 대상 재확인이 남았다. (review-1 #5, 일부 해소) | 레거시 기동 후 `cd characterization && npm test` 로 스냅샷이 여전히 레거시와 맞는지 확인한다. 테스트 코드 · 스냅샷은 고치지 않는다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchCondition.java:173-180` | `phpParam` 은 `name[]` 만 배열로 본다. PHP 는 `name[0]=` · `name[k]=` 도 배열로 받아 `reset()` 으로 첫 값을 쓰고(`legacy/item-bank-php/search.php:53-80`), `tag=a&tag[]=b` 처럼 섞이면 뒤에 온 쪽이 이긴다. 여기서는 항상 `name[]` 이 이긴다. 현재 스냅샷 케이스(`tag[]`)에는 영향 없음. (review-1 #6) | 의심 동작 목록에 남기고, 필요하면 `name[...]` 키 전반과 파라미터 순서를 반영한다 |
| 5 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchFixtures.java:12` | `CLAUDE.md:79` "픽스처는 `ItemFixtures`" 인데 별도 픽스처 클래스를 만들었다. (review-1 #7) | `ItemFixtures` 에 `searchRow(...)` 로 합친다 |
| 6 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchServiceTest.java:23-24` | Mockito 단위 테스트에 `@ActiveProfiles("test")` 가 붙어 있다. 스프링 컨텍스트를 띄우지 않아 효과가 없다. (review-1 #8) | 어노테이션을 지운다 |
| 7 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchRowRepositoryTest.java:34`, `:46-47` | 단원 엔티티는 `M5-1` 하나뿐인데 뷰 행 c · d 의 `unit_code` 를 `M5-2` 로 넣었다. 실제 뷰라면 나올 수 없는 조합이라 픽스처가 실제 데이터 모양과 어긋난다 | `M5-2` 단원을 persist 하고 c · d 를 그 단원에 연결한다 |

## 확인 필요

- `characterization`(레거시 대상) — 8081 이 꺼져 있어 실행하지 못했다. `docker compose --profile php up -d` 후 `cd characterization && npm test` 를 돌려 통과 · 실패 수를 이 문서에 덧붙여 달라.
- 경로 `tag[]` 원문 대괄호 — `characterization/lib/target.mjs:42` 는 `URLSearchParams` 로 `%5B%5D` 인코딩해 보낸다. 브라우저 · 클라이언트가 인코딩하지 않은 `[` `]` 를 보내면 Tomcat 이 400 으로 막을 수 있는데(레거시 PHP 는 받아 줌) 이번에 재현하지 않았다.

## 의심 동작 (레거시에서 옮긴 그대로, 이관 중 고치지 않음)

- 난이도 빈값이면 `level < 5` 로 난이도 5 가 빠진다(`legacy/item-bank-php/search.php:214-215`). 주석은 "1~5 모두 포함"이라고 적혀 있다(`:213`).
- 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`legacy/item-bank-php/search.php:97`).

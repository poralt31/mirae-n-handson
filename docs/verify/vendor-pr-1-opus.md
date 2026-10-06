# 외주 PR 1 검증 — `vendor-prs/pr-1-missing-tests.patch`

- 검증 방식: patch 미적용, 내용만 읽어 판정 (`git apply --stat` · `git apply --check` 만 실행 — check 통과)
- 요청 범위(Subject): **단원별 문항 수 통계 API 추가**
- 변경 파일(모두 `modern/api`):
  - `modern/api/src/main/java/com/example/item/UnitController.java` (+6)
  - `modern/api/src/main/java/com/example/item/UnitItemCountResponse.java` (신규, +9)
  - `modern/api/src/main/java/com/example/item/UnitService.java` (+16 −1)
- 줄번호는 patch 적용 후 파일 기준(hunk 헤더 `+21` · `+28` · 신규 파일 `+0,9` 로 계산).

```
판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 확인 필요 | 지시에 따라 `cd modern/api && ./gradlew test` 를 실행하지 않음. 커밋 메시지의 "기존 테스트 전부 통과 확인(./gradlew test)"은 외주사 주장일 뿐 검증되지 않음. 또한 체크리스트 1의 "테스트가 충분하다"는 미충족 — 새 기능에 대한 테스트가 0건(#1, #2) |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 비밀값 · 문자열 SQL · 데이터 삭제/덮어쓰기 없음(조회 전용, 파생 쿼리 `countByUnitIdAndStatus` 바인딩 사용) |
| 요청 범위 이탈 없음 | 충족 | 요청 "단원별 문항 수 통계 API 추가" ↔ 컨트롤러 엔드포인트 · 응답 DTO · 서비스 메서드 3개 파일 모두 이 요청으로 설명됨. 의존성 · 스키마 · 설정 변경 없음 |
| 컨벤션 준수 | 충족 | Controller → Service → Repository 준수(컨트롤러는 서비스만 호출), record DTO 반환, 클래스 단위 `@Transactional(readOnly = true)` 안에서 DTO 변환, `status = 'A'` 만 집계, try/catch · `System.out` 없음. Lombok 규칙은 아래 "확인 필요" 참고 |

이슈 목록
| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | modern/api/src/main/java/com/example/item/UnitService.java:36 | 신규 `countActiveItemsByUnit()` 에 대한 서비스 테스트가 없음. patch 에 `src/test` 변경이 0건이고, 기존 `UnitServiceTest` 는 `listUnits` 2건만 다룸. 설명에 적힌 "단원 목록과 같은 순서", "문항 0개 단원 포함", "ACTIVE 만 집계" 어느 것도 테스트로 고정되지 않음 | `UnitServiceTest` 에 `@Mock ItemRepository` 추가 후 Mockito 로 ① 순서 유지 ② 0개 단원 포함 ③ `ItemStatus.ACTIVE` 로 호출됨(verify) ④ 단원 없음 → 빈 목록 케이스 추가. 한국어 `@DisplayName` |
| 2 | 경고 | modern/api/src/main/java/com/example/item/UnitController.java:26 | 신규 엔드포인트 `GET /api/units/item-counts` 에 대한 컨트롤러 테스트가 없음(CLAUDE.md: 컨트롤러는 `@WebMvcTest` + `@MockBean`). 같은 `/api/units/**` 아래에 `ItemController` 의 `/units/{code}/items` 가 있어 경로 매핑을 테스트로 확인해 둘 가치가 있음 | `@WebMvcTest(UnitController.class)` 로 200 · JSON 필드(`code`, `name`, `grade`, `activeItemCount`) 검증 테스트 추가. 리포지토리 count 쿼리는 `ItemRepositoryTest`(`@DataJpaTest`)에 상태별 카운트 케이스 추가 |
| 3 | 경고 | modern/api/src/main/java/com/example/item/UnitService.java:39 | 단원마다 `itemRepository.countByUnitIdAndStatus(...)` 를 호출하는 N+1 쿼리(단원 N개 → 쿼리 1+N회). 하나의 readOnly 트랜잭션 안에서 반복되므로 단원 수가 늘면 커넥션 점유 시간이 길어지고, Hikari 풀이 작아(최대 5, 대기 3초) 동시 요청 시 풀 고갈로 이어질 수 있음 | `select i.unit.id, count(i) from Item i where i.status = :status group by i.unit.id` 같은 집계 쿼리 1회로 가져와 `Map<unitId, count>` 로 합치고, 없는 단원은 0 으로 채움 |
| 4 | 제안 | modern/api/src/main/java/com/example/item/UnitItemCountResponse.java:4 | 기존 `UnitResponse` 는 `id` 를 포함하는데 새 DTO 는 `id` 가 빠져 단원 응답 모양이 엔드포인트마다 다름 | 화면에서 단원 목록과 매칭할 필요가 있으면 `Integer id` 를 포함해 `UnitResponse` 와 필드 구성을 맞춤 |
| 5 | 제안 | modern/api/src/main/java/com/example/item/UnitItemCountResponse.java:6 | CLAUDE.md 의 관례는 `XxxResponse.from(entity)` 인데 팩터리 이름이 `of` 임(인자가 2개라 `of` 도 합리적이므로 승인에는 영향 없음) | 팀 관례를 정해 `from(Unit, long)` 으로 맞추거나, 2인자 팩터리는 `of` 로 한다는 규칙을 CLAUDE.md 에 명시 |
```

## 확인 필요

- `modern/api` 테스트 — 지시에 따라 미실행. `cd modern/api && ./gradlew test` 를 `review/pr-1` 브랜치에서 실행해 통과 · 실패 수를 확인해야 함. 참고로 기존 `UnitServiceTest` 는 `@InjectMocks` 생성자 주입이라 `ItemRepository` mock 이 없으면 Mockito 가 `null` 을 넘기므로 기존 2건은 컴파일 · 통과할 것으로 보이나, 실행으로 확인되지 않음.
- `CLAUDE.md` 의 "DTO 는 Lombok library 로 관리" 규칙 — 새 DTO 는 Lombok 없는 record 다. 다만 기존 DTO(`UnitResponse` 등)도 모두 Lombok 없는 record 이고 "코드가 우선" 원칙상 위반으로 단정할 수 없음. 규칙의 적용 대상(record 에도 Lombok 을 쓰라는 것인지)을 팀이 확정해야 함.
- `modern/web` 연동 — 이번 PR 은 API 만 추가하고 `src/api/types.ts` 등 프런트 타입은 건드리지 않음. 요청 범위상 API 만이면 문제 없으나, 화면 연동까지 요청에 포함됐는지는 요청자 확인 필요.

## 반려 사유 요약

1. "테스트 통과" 기준이 확인 필요 상태(미실행)이며, 새 기능 테스트가 0건이라 "테스트가 충분하다" 항목이 미충족이다(#1, #2).
2. 재제출 시 #1 · #2 테스트 추가와 #3 N+1 해소를 권고하고, `./gradlew test` 실행 결과(통과 · 실패 수)를 첨부받는다.

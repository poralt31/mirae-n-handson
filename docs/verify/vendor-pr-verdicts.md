# 외주사 PR 판정 기록

판정 기준: `templates/approval-checklist.md` (네 기준이 모두 충족되어야 승인). 절차: `templates/verification-loop.md`.
요청 범위는 각 patch 의 `Subject:` 줄. 테스트는 세 건 모두 같은 기준점 `61a1113` 위에 patch 하나씩 적용해 `cd modern/api && ./gradlew test --rerun` 으로 실행했다(pr-1 은 `review/pr-1` 브랜치, pr-2 · pr-3 은 커밋 없는 임시 워크트리). 줄번호는 patch 적용 후 파일 기준이다. 세 건 모두 레거시 이관이 아니라서 characterization 은 실행 대상이 아니다.

| PR | 판정 | 미충족 기준 | 근거(파일:줄번호) | 외주사에 돌려보낼 수정 요청 |
|---|---|---|---|---|
| pr-1-missing-tests<br>요청: 단원별 문항 수 통계 API 추가 | 반려 | 테스트 통과(테스트 충분성) | 테스트 44건 통과 · 실패 0이지만 모두 기존 테스트다. 새 코드 `item/UnitController.java:26-29` · `item/UnitService.java:36-43` 를 부르는 테스트가 0건이다. 기존 `UnitServiceTest.java:19-23` 에 `ItemRepository` 목이 없어 `@InjectMocks` 가 null 을 넣는다. (경고) `UnitService.java:37-39` 는 단원마다 count 쿼리를 날린다(N+1). | 1) `UnitServiceTest` 에 `@Mock ItemRepository` 와 `countActiveItemsByUnit` 테스트를 추가해 주세요. 순서 유지, 문항 0개 단원 포함, `ACTIVE` 만 세는지 확인해야 합니다. 2) `GET /api/units/item-counts` 의 `@WebMvcTest` 를 추가해 주세요. 3) 상태 `R` · `D` 가 제외되는지 `@DataJpaTest` 로 확인해 주세요. 4) 단원별 count 를 group by 쿼리 한 번으로 바꿔 주세요. |
| pr-2-hardcoded-secret<br>요청: 외부 채점 서버 연동 클라이언트 추가 | 반려 | 치명 이슈 0건 | 테스트 45건 통과 · 실패 0. (치명) `assignment/GradingServerClient.java:28-29` 에 채점 서버 계정과 비밀번호가 리터럴로 들어 있다. 저장소 이력에 남으므로 커밋하는 순간 유출로 본다. (경고) `GradingServerClient.java:56` 은 JSON 본문을 문자열로 이어 붙인다. `studentId` 에 `"` 가 들어가면 본문이 깨지거나 필드가 주입된다. (경고) `GradingServerClientTest` 1건은 `buildRequest` 만 보고, 오류 응답 · 타임아웃 경로(`:38-52`)는 테스트하지 않는다. | 1) 계정 · 비밀번호를 코드에서 빼고 `grading.username` · `grading.password` 설정(환경 변수 주입)으로 받아 주세요. 기본값은 두지 마세요. 2) 이미 노출된 비밀번호는 폐기 · 교체해 주세요. 3) 요청 본문은 Jackson `ObjectMapper` 같은 직렬화기로 만들어 주세요. 4) 200 외 응답, `IOException` 경로의 테스트를 추가해 주세요(로컬 스텁 서버 등). |
| pr-3-out-of-scope<br>요청: 문항 검색에 난이도 필터 추가 | 반려 | 요청 범위 이탈 없음 | 테스트 46건 통과 · 실패 0. 요청과 무관한 "주변 코드 정리"가 들어 있다. `assignment/DistributionService.java:52`, `:62-67`(재배포 쓰기 경로의 마감 검사 추출)과 `assignment/ReportService.java:65`, `:71-72`, `:82-89`(평균 계산 추출)이다. 특히 `ReportService.java:71-72` 는 로그에 평균 점수를 추가해 커밋 메시지의 "동작 변화 없음"과도 다르다. (경고) `item/UnitService.java:43-44` 는 단원 문항을 전부 읽은 뒤 메모리에서 난이도를 거른다. (경고) 같은 경로 `/api/units/{code}/items` 가 `ItemController.java:43` 과 `UnitController.java:31`(`params = "level"`)로 나뉘었는데, 이 라우팅을 확인하는 `@WebMvcTest` 가 없다. | 1) `DistributionService` · `ReportService` 변경을 이 PR 에서 빼 주세요. 필요하면 별도 PR 로 요청 범위를 받아 다시 보내 주세요. 2) 난이도 필터는 저장소 쿼리 조건(`level`)으로 걸러 주세요. 3) 기존 `listItemsByUnit` 과 같은 컨트롤러에서 `level` 을 선택 파라미터로 받도록 합쳐 주세요. 4) `level` 유무에 따른 라우팅 · 400 동작을 `@WebMvcTest` 로 추가해 주세요. |

## 확인 필요

- pr-3 — 요청 문장은 "문항 **검색**에 난이도 필터"인데, 구현은 단원별 문항 조회(`/api/units/{code}/items`)에 붙였다. 발주 측이 뜻한 화면이 검색(`/api/items/search`, 레거시 `search.php`)인지 단원 조회인지 사람이 확인해야 한다. 검색 쪽이라면 `day1` 의 `/api/items/search` 는 이미 `level` 을 받는다.
- pr-1 · pr-3 — 둘 다 `UnitService` 생성자에 `ItemRepository` 를 추가한다(`UnitService.java:16-21`). 두 PR 이 모두 고쳐져 들어오면 머지 충돌이 난다. 머지 순서를 정해야 한다.

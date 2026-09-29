# 문항 은행(legacy/item-bank-php) 비즈니스 규칙 — 코드 기준 독립 추출

- 근거 범위: `legacy/item-bank-php/` 의 `search.php`, `register.php`, `units.php`, `index.php` (`inc/`, `vendor/`, `Dockerfile` 은 규칙과 무관해 제외)
- `docs/` 는 읽지 않았다. 다른 문서와 독립적으로 비교하기 위한 산출물이다.
- 주석과 코드가 다르면 코드를 기준으로 썼다.

## 범위 한계

- **수정(update) 기능이 없다.** 폴더에 등록(`register.php`)만 있고 수정 화면 · 처리 파일은 없다. 그래서 "등록 · 수정 시 검증" 중 등록 검증만 다룬다.
- 공개 문항 뷰 `v_item_public` 의 정의(어떤 상태를 걸러내는지)는 이 폴더 밖(DB 스키마)에 있어서 다루지 않는다. 이 폴더 코드에서 확인되는 것은 "검색은 이 뷰만 조회한다"는 사실뿐이다(CX-14).

## A. 검색 조건 조합 (`search.php`)

### CX-01 — 검색 조건은 AND 로 결합한다
- 규칙: 키워드 · 단원 · 난이도 · 태그 조건은 모두 `AND` 로 이어 붙는다.
- 근거: `search.php:44`, `:98`, `:118`, `:216`, `:226`, `:244`
- 코드:
  ```php
  $where   = " WHERE 1=1";
  $where .= " AND unit_code = ?";
  ```

### CX-02 — 키워드는 제목 또는 지문의 부분 일치다
- 규칙: 키워드를 앞뒤 공백 제거 후 `title` 또는 `stem` 에 `LIKE %키워드%` 로 찾는다. 빈 키워드면 조건을 넣지 않는다.
- 근거: `search.php:85`, `:97-98`
- 코드:
  ```php
  $q = trim($q);
  $like = '%' . $q . '%';
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```

### CX-03 — 키워드는 100자로 자르고, `%` · `_` 는 이스케이프하지 않는다
- 규칙: 100자를 넘으면 앞 100자만 쓰고 경고한다. `%` · `_` 는 와일드카드로 그대로 동작하며 경고만 낸다.
- 근거: `search.php:86-89`, `:94-96`
- 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
  if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
  ```

### CX-04 — 단원은 코드 정확 일치이며, 형식이 틀려도 그대로 조회한다
- 규칙: 단원 코드는 `unit_code = ?` 로 정확히 비교한다. 형식 위반 · 소문자 · 미등록 코드는 경고만 내고 입력값 그대로 조회한다(대소문자 변환 없음).
- 근거: `search.php:111-120`, `:141-143`
- 코드:
  ```php
  if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
      // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
  $where .= " AND unit_code = ?";
  ```

### CX-05 — 난이도를 비우면 난이도 5 문항이 제외된다 (주석과 코드 불일치)
- 규칙: 난이도가 빈값이면 `level < 5` 조건이 붙어 **난이도 5 문항은 결과에서 빠진다.** 주석은 "전체 난이도 검색 (1~5 모두 포함)" 이라고 하지만 코드는 다르다. 요약(`summary`)에도 조건이 표시되지 않아 "조건 없이 검색했습니다"로 보인다(`:355-356`).
- 근거: `search.php:213-216`
- 코드:
  ```php
  // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
  if ($level == '') {
      $where .= " AND level < 5";
  ```

### CX-06 — 난이도 1~5 는 정확 일치, 범위 밖 값은 정수로 바꿔 비교한다
- 규칙: `1`~`5` 한 자리는 `level = ?`. 그 외 값은 경고 후 `(int)` 로 바꿔 `level = ?` 로 조회한다(대개 0건). 입력이 `5` 이면 `level < 5` 제외가 적용되지 않으므로 5 문항이 나온다.
- 근거: `search.php:217-229`
- 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
  ...
  $warnings[] = '난이도는 1~5 사이여야 합니다.';
  $values[] = (int)$level;
  ```

### CX-07 — 태그는 이름 정확 일치이고, 검색 태그는 1개만 받는다
- 규칙: `tag` 하나에 대해 `item_tag`·`tag` 를 `EXISTS` 로 조인해 `t.name = ?` 인 문항만 남긴다. 부분 일치는 없다. 50자를 넘으면 잘라 쓴다. 미등록 태그도 조회는 하고 경고만 낸다.
- 근거: `search.php:236-249`, `:240-243`, `:258-260`
- 코드:
  ```php
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```

### CX-08 — 같은 이름의 파라미터가 배열로 오면 첫 값만 쓴다
- 규칙: `q`, `unit`, `level`, `tag`, `sort`, `dir`, `page` 가 배열로 들어오면 첫 원소만 사용한다.
- 근거: `search.php:53-80`
- 코드:
  ```php
  $unit = is_array($params['unit']) ? (string)reset($params['unit']) : (string)$params['unit'];
  ```

## B. 목록 정렬 · 페이지 · 제외 조건 (`search.php`, `units.php`)

### CX-09 — 기본 정렬은 난이도 내림차순, 같으면 ID 오름차순이다
- 규칙: `sort` 가 비어 있으면 `level DESC, id ASC`.
- 근거: `search.php:317-320`
- 코드:
  ```php
  case '':
      // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
      $orderBy = " ORDER BY level DESC, id ASC";
  ```

### CX-10 — 알 수 없는 정렬 기준은 경고 후 기본 정렬로 대체한다
- 규칙: `id|title|unit|level|created` 외의 `sort` 는 경고하고 CX-09 와 같은 정렬을 쓴다. 대소문자는 무시하고(`strtolower`) 앞뒤 공백은 제거한다.
- 근거: `search.php:266`, `:322-326`
- 코드:
  ```php
  $sort = strtolower(trim($sort));
  default:
      $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
  ```

### CX-11 — 정렬 기준별 방향 기본값과 보조 정렬이 다르다
- 규칙: `dir` 은 `asc`/`desc` 만 인정한다(그 외는 경고 후 빈값). 기준별 동작은 아래와 같다.
  - `id`: `desc` 면 내림차순, 아니면 오름차순
  - `title`: 기본 오름차순, 보조 `id ASC`
  - `unit`: 기본 오름차순 `unit_code`, 보조 `level DESC, id ASC` (방향은 `unit_code` 에만 적용)
  - `level`: **`dir=asc` 일 때만 오름차순**, 그 외(빈값 포함)는 내림차순. 보조 `id ASC`
  - `created`: `dir=asc` 일 때만 오름차순, 그 외 내림차순. 보조 `id ASC`
- 근거: `search.php:268-273`, `:301-315`, `:293-299`
- 코드:
  ```php
  case 'level':
      if ($dir === 'asc') { $orderBy = " ORDER BY level ASC, id ASC";
      } else {              $orderBy = " ORDER BY level DESC, id ASC";
  ```

### CX-12 — 한 페이지 20건, 페이지 번호는 1~999 로 보정한다
- 규칙: `LIMIT 20 OFFSET (page-1)*20`. 숫자가 아니면(빈값 · `1` 제외) 경고 후 1페이지, 1 미만이면 1, 999 초과면 999 로 보정한다.
- 근거: `search.php:336-349`, `:523`
- 코드:
  ```php
  if ($page > 999) { $page = 999;
  $offset = ($page - 1) * 20;
  $limit  = " LIMIT 20 OFFSET " . (int)$offset;
  ```

### CX-13 — 건수는 필터가 같은 별도 COUNT 로 세며, 0건이면 표 대신 메시지를 낸다
- 규칙: 총 건수는 목록과 같은 `FROM`·`WHERE` 로 `COUNT(*)` 한다(페이지 크기와 무관). 0건이면 `<p id="message">` 만 출력하고 표를 그리지 않는다. 페이지 링크는 총 페이지가 2 이상일 때만 나온다.
- 근거: `search.php:526`, `:655-660`, `:685-686`
- 코드:
  ```php
  $countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
  if ($total === 0) { echo '<p id="message">검색 결과가 없습니다</p>' . "\n";
  ```

### CX-14 — 검색은 공개 문항 뷰(`v_item_public`)만 조회한다
- 규칙: 검색 대상은 `item` 테이블이 아니라 `v_item_public` 뷰다. 이 폴더의 코드만으로는 뷰가 걸러내는 조건을 알 수 없다(범위 한계 참고). `tag_names` 가 NULL 이면 빈 문자열로 표시한다.
- 근거: `search.php:521-522`, `:678`
- 코드:
  ```php
  $select = "SELECT id, title, unit_code, level, tag_names, created_at";
  $from   = " FROM v_item_public";
  ```

### CX-15 — 단원 목록의 공개 문항 수는 `status='A'` 만 센다
- 규칙: 단원별 문항 수에는 활성(`A`) 문항만 포함하고 삭제 · 검수중 문항은 제외한다. 단원은 학년 → 코드 오름차순.
- 근거: `units.php:15-19`
- 코드:
  ```php
  // 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
  ORDER BY u.grade ASC, u.code ASC
  ```

## C. 등록 시 검증 (`register.php`)

### CX-16 — 제목은 5자 이상 200자 이하다 (앞뒤 공백 제거 후 글자 수)
- 규칙: `trim` 후 `mb_strlen` 이 5 미만이거나 200 초과면 오류. 공백만 있는 내부 문자는 세어진다.
- 근거: `register.php:41`, `:48-54`
- 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') < 5) {
  if (mb_strlen($title, 'UTF-8') > 200) {
  ```

### CX-17 — 지문은 필수이며 길이 제한이 없다
- 규칙: `trim` 후 빈 문자열이면 오류. 상한 검사는 코드에 없다.
- 근거: `register.php:42`, `:56-58`
- 코드:
  ```php
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  ```

### CX-18 — 단원은 필수이며 등록된 단원 ID 여야 한다
- 규칙: 폼의 `unit_id` 가 `unit` 테이블 목록의 `id` 와 문자열로 일치해야 한다. 아니면 "단원을 선택해야 합니다."
- 근거: `register.php:27-31`, `:60-68`
- 코드:
  ```php
  if ((string)$u['id'] === $unitId) { $unitOk = true; }
  if (!$unitOk) {
  ```

### CX-19 — 난이도는 필수이며 1~5 한 자리 정수다
- 규칙: 정규식 `^[1-5]$` 을 통과해야 한다(빈값 오류).
- 근거: `register.php:70-72`
- 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  ```

### CX-20 — 태그는 선택 사항이고, 목록에 없는 태그 ID 는 오류 없이 버린다
- 규칙: 선택한 태그 중 `tag` 목록에 있는 ID 만 저장한다. 없는 ID 는 오류 없이 무시한다. 태그 개수 제한은 없다.
- 근거: `register.php:45`, `:74-81`
- 코드:
  ```php
  $validTagIds = array();
  ... if ((string)$t['id'] === (string)$tid) { $validTagIds[] = (int)$tid; }
  ```

### CX-21 — 등록한 문항은 검수중(`R`) 상태로 저장되어 검색에 나오지 않는다
- 규칙: INSERT 시 `status` 는 항상 `'R'` 이다. 공개(`A`) 전환 코드는 이 폴더에 없다(CX-15 처럼 `A` 만 공개로 센다).
- 근거: `register.php:84`, `:92-95`, `:116`
- 코드:
  ```php
  VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())
  $notice = '문항 #' . $newId . ' 을(를) 등록했습니다. 검수 완료 후 검색에 노출됩니다.';
  ```

### CX-22 — 새 문항 ID 는 `MAX(id)+1` 로 채번하고, 문항과 태그를 한 트랜잭션에 저장한다
- 규칙: `FOR UPDATE` 로 잠근 뒤 `MAX(id)+1` 을 ID 로 쓴다. 문항 · 태그 저장 중 하나라도 실패하면 롤백하고 "저장 중 오류가 발생했습니다."를 낸다. 검증 오류가 하나라도 있으면 저장하지 않는다.
- 근거: `register.php:83-90`, `:104-114`, `:119-123`
- 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  $conn->commit();
  } catch (Exception $e) { $conn->rollback();
  ```

## 주석과 코드가 다른 곳 요약

| 위치 | 주석 | 실제 코드 | 규칙 |
|---|---|---|---|
| `search.php:213-216` | 전체 난이도(1~5 모두 포함) | `level < 5` 로 5 제외 | CX-05 |
| `register.php:48` | "공백 제외 아님, 앞뒤 공백만 제거" | 일치 (`trim` 후 길이 검사) | CX-16 |

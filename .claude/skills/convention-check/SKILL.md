---
name: convention-check
description: 변경된 코드(또는 지정한 경로)가 이 저장소 CLAUDE.md 의 코딩 컨벤션 · 금지 사항을 지키는지 예/아니오로 점검하고 위반을 파일:줄번호로 보고한다. 코드는 고치지 않는다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 어긴 데 있는지 봐 줘" 같은 요청에 쓴다.
argument-hint: "[경로 ...] (생략하면 git 변경 파일 전체)"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

## 반드시 지킬 것
- 코드를 직접 고치지 않는다. 판정과 수정 방향만 보고한다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.

## 1. 점검 대상 정하기
- 인자 `$ARGUMENTS` 가 있으면 그 경로(파일 · 폴더)만 본다. 폴더는 Glob 으로 펼친다.
- 없으면 변경 파일 전체를 모은다.
  - `git status --short` — `??`(아직 add 하지 않은 새 파일) 포함 모든 줄의 경로
  - `git diff HEAD --name-only` — 스테이징 여부와 관계없이 수정된 파일
- 삭제된 파일은 8번 항목 판정에만 쓰고 내용은 읽지 않는다.
- 줄번호는 Read · Grep 결과의 실제 줄번호만 쓴다. 기존 파일은 `git diff HEAD -- <파일>` 로 바뀐 줄을 우선 본다.

## 2. 점검 항목 (예 = 통과)
| # | 항목 | 대상 | 아니오로 판정하는 근거 |
|---|---|---|---|
| 1 | 컨트롤러가 Repository 를 직접 참조하지 않는다 | `modern/api/**/*Controller.java` | `*Repository` import · 필드 · 생성자 인자 |
| 2 | 컨트롤러가 엔티티 대신 record DTO 를 반환한다 | 같음 | 매핑 메서드 반환 타입(제네릭 안 포함)이 `@Entity` 클래스 |
| 3 | 서비스 조회 메서드가 `@Transactional(readOnly = true)` 안에 있다 | `modern/api/**/*Service.java` | 쓰기 없는 public 메서드에 클래스 · 메서드 어디에도 readOnly 트랜잭션이 없음 |
| 4 | 예외를 삼키지 않고 HTTP 변환은 `GlobalExceptionHandler` 한 곳에서만 한다 | `modern/api/src/main/**` | catch 블록에서 다시 던지지 않음, 다른 클래스의 `@ExceptionHandler` · `@ControllerAdvice` |
| 5 | 현재 시각은 주입받은 `Clock` 으로 얻는다 | `modern/api/src/main/**` | 인자 없는 `LocalDateTime.now()` · `Instant.now()` · `LocalDate.now()` 등 |
| 6 | 웹의 HTTP 호출은 `getJson` 을 거친다 | `modern/web/src/**` (`src/test/`, `src/api/client.ts` 제외) | `fetch(` 직접 호출 |
| 7 | 테스트 메서드에 한국어 `@DisplayName` 이 있다 | `modern/api/src/test/**` | `@Test` 메서드에 `@DisplayName` 이 없거나 한글이 없음 |
| 8 | 원본 보존 규칙을 지켰다 | 변경 파일 경로 목록 | 아래 중 하나라도 해당 |

8번 세부:
- `legacy/`, `incident-logs/`, `vendor-prs/`, `characterization/__snapshots__/` 아래 파일이 바뀌었다.
- `package-lock.json` 이 바뀌었다(`npm install` 흔적).
- `legacy/grade-mssql/sql/usp_*.sql` 과 `db/mssql/init/04-procs.sql` 중 한쪽만 바뀌었다.

대상 파일이 하나도 없는 항목은 "해당 없음"으로 적는다.
`@EntityGraph` 필요 여부, 외부 노출 쿼리의 `status='A'` 조건처럼 판단이 필요한 것은 판정하지 않고, 눈에 띄면 "확인 필요"에만 적는다.

## 3. 출력 형식 (순서 고정)

### 판정 요약
점검 대상 파일 N개. 항목별 한 줄로 `통과` / `위반 n건` / `해당 없음` 을 적는다.

| # | 항목 | 판정 |
|---|---|---|

### 위반 목록
| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|

위반이 없으면 표 대신 "위반 없음" 한 줄. 표 아래에 "확인 필요" 항목을 `파일:줄번호 — 이유` 로 짧게 덧붙인다(없으면 생략).

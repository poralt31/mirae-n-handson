// 동작 보존 테스트 — 과제 배포(assignment)의 과제 목록 /assignments
//
// 표 머리글 과제명 · 마감 · 배포 학급 수는 normalize.mjs 의 HEADER_MAP 에 없어 col2 · col4 · col6 으로 남는다.
// 마감 지난 행의 past 표시는 <tr> class 라서 스냅샷에 남지 않는다.
// 기대값은 손으로 적지 않는다(npm run baseline -- assignment).
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'assignment';
const PATH = '/assignments';

describe('assignment · 과제 목록(/assignments)', () => {
  it('조건 없는 기본 조회 — 삭제(D) 제외, 마감 · ID 순', async () => {
    expect(await fetchNormalized(MODULE, PATH)).toMatchSnapshot();
  });
});

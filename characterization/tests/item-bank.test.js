// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 search.php
//
// 규칙 ID(CX-xx)는 docs/item-bank/CROSS-CHECK.md 기준이다.
// 스냅샷에는 status · rows · count · message 만 남고 경고 문구는 남지 않는다.
// 그래서 규칙이 행 목록이나 건수로 드러나는 값을 골랐다.
// 기대값은 손으로 적지 않는다(npm run baseline -- item-bank).
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

const search = (params) => fetchNormalized(MODULE, PATH, params);

describe('item-bank · 문항 검색(search.php)', () => {
  describe('정상 입력', () => {
    it('조건 없는 기본 검색 — CX-05 · CX-09 · CX-12 · CX-13 · CX-14', async () => {
      // 시드상 공개이면서 난이도 5 미만인 문항이 정확히 20건(페이지 크기와 같다)
      expect(await search()).toMatchSnapshot();
    });

    it('단원 + 난이도 조합 — CX-01 · CX-04 · CX-06 · CX-14', async () => {
      // M5-1 · 난이도 3 에는 공개 문항(12)과 검수 중 문항(17)이 함께 있다
      expect(await search({ unit: 'M5-1', level: '3' })).toMatchSnapshot();
    });

    it('키워드 + 태그 + 제목 정렬 — CX-01 · CX-02 · CX-07 · CX-11', async () => {
      expect(await search({ q: '문장제', tag: '문장제', sort: 'title' })).toMatchSnapshot();
    });
  });

  describe('경계값', () => {
    it('난이도 5 명시 — CX-05 · CX-06', async () => {
      // 비우면 빠지는 난이도 5가 명시하면 나오는지
      expect(await search({ level: '5' })).toMatchSnapshot();
    });

    it('앞에 0이 붙은 난이도 05 — CX-06', async () => {
      // ^[1-5]$ 는 통과하지 못하지만 (int) 로 바뀌면 5가 된다
      expect(await search({ level: '05' })).toMatchSnapshot();
    });

    it('마지막 페이지 바로 다음 page=2 — CX-12 · CX-13', async () => {
      // 전체 20건이면 2페이지는 건수는 있고 행은 없다
      expect(await search({ page: '2' })).toMatchSnapshot();
    });

    it('페이지 상한 초과 page=1000 — CX-12', async () => {
      expect(await search({ page: '1000' })).toMatchSnapshot();
    });

    it('키워드 101자는 100자로 잘린다 — CX-02 · CX-03', async () => {
      // 잘리면 끝의 '없' 이 사라지고 % 만 남아 전부 걸린다. 자르지 않으면 0건
      expect(await search({ q: '%'.repeat(100) + '없' })).toMatchSnapshot();
    });

    it('키워드 100자는 잘리지 않는다 — CX-03', async () => {
      expect(await search({ q: '%'.repeat(99) + '없' })).toMatchSnapshot();
    });

    it('정렬 기준 대소문자 · 앞뒤 공백 — CX-10 · CX-11', async () => {
      // 공백을 지우고 소문자로 바꾸면 level 이 된다. level 은 dir=asc 일 때만 오름차순
      expect(await search({ sort: ' Level ', dir: 'asc' })).toMatchSnapshot();
    });
  });

  describe('빈 값 · 누락', () => {
    it('모든 파라미터가 빈 문자열 — CX-02 · CX-05 · CX-09 · CX-12', async () => {
      // 파라미터가 없을 때(기본 검색)와 같게 취급되는지
      expect(
        await search({ q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' }),
      ).toMatchSnapshot();
    });

    it('공백만 있는 난이도 — CX-05', async () => {
      expect(await search({ level: '  ' })).toMatchSnapshot();
    });
  });

  describe('이상한 값', () => {
    it('음수 난이도 — CX-06 · CX-13', async () => {
      expect(await search({ level: '-1' })).toMatchSnapshot();
    });

    it('음수 페이지 — CX-12', async () => {
      // 숫자 정규식을 통과하지 못해 1페이지로 처리된다
      expect(await search({ page: '-3' })).toMatchSnapshot();
    });

    it('형식은 맞지만 없는 단원 코드 — CX-04 · CX-13', async () => {
      expect(await search({ unit: 'Z9-99' })).toMatchSnapshot();
    });

    it('소문자 단원 코드 — CX-04', async () => {
      // 코드는 대소문자를 바꾸지 않지만 DB 콜레이션(_ci)은 대소문자를 구분하지 않는다
      expect(await search({ unit: 'm5-1' })).toMatchSnapshot();
    });

    it('태그가 배열로 들어옴 — CX-07 · CX-08', async () => {
      // PHP 는 tag[] 로 받아야 배열이 된다. 첫 값(도형)을 쓰면 1건이 나온다
      expect(await search({ 'tag[]': ['도형', '계산'] })).toMatchSnapshot();
    });
  });
});

package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/** 검색 파라미터 해석 — 레거시 search.php(PHP 7.4) 와 같은 값이 나오는지. */
class ItemSearchConditionTest {

    private static ItemSearchCondition condition(Map<String, List<String>> params) {
        return ItemSearchCondition.from(params);
    }

    @Test
    @DisplayName("파라미터가 없으면 키워드 · 단원 · 태그 없음, 난이도 5 제외, 기본 정렬, 1페이지")
    void emptyParamsUseDefaults() {
        ItemSearchCondition c = condition(Map.of());

        assertThat(c.getKeyword()).isNull();
        assertThat(c.getUnitCode()).isNull();
        assertThat(c.getLevel()).isNull();
        assertThat(c.getTag()).isNull();
        assertThat(c.getSort()).isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(c.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드는 trim 후 코드 포인트 기준 100자로 자른다")
    void keywordIsTrimmedAndCutAt100() {
        assertThat(ItemSearchCondition.parseKeyword("  분수 \t")).isEqualTo("분수");
        assertThat(ItemSearchCondition.parseKeyword("%".repeat(100) + "없")).isEqualTo("%".repeat(100));
        assertThat(ItemSearchCondition.parseKeyword("%".repeat(99) + "없")).isEqualTo("%".repeat(99) + "없");
        assertThat(ItemSearchCondition.parseKeyword("😀".repeat(101))).isEqualTo("😀".repeat(100));
        assertThat(ItemSearchCondition.parseKeyword("   ")).isNull();
    }

    @Test
    @DisplayName("난이도: 빈값은 null(level<5), 1~5 는 그대로, 그 밖은 PHP (int) 변환")
    void levelFollowsPhpIntCast() {
        assertThat(ItemSearchCondition.parseLevel("")).isNull();
        assertThat(ItemSearchCondition.parseLevel("  ")).isNull();
        assertThat(ItemSearchCondition.parseLevel("3")).isEqualTo(3L);
        assertThat(ItemSearchCondition.parseLevel("05")).isEqualTo(5L);
        assertThat(ItemSearchCondition.parseLevel("-1")).isEqualTo(-1L);
        assertThat(ItemSearchCondition.parseLevel("abc")).isEqualTo(0L);
        assertThat(ItemSearchCondition.parseLevel("3abc")).isEqualTo(3L);
        assertThat(ItemSearchCondition.parseLevel("1e1")).isEqualTo(10L);
        assertThat(ItemSearchCondition.parseLevel("4.9")).isEqualTo(4L);
        assertThat(ItemSearchCondition.parseLevel("99999999999999999999")).isEqualTo(Long.MAX_VALUE);
        assertThat(ItemSearchCondition.parseLevel("1e999")).isEqualTo(0L);
    }

    @Test
    @DisplayName("페이지: 숫자만 인정(trim 안 함), 1 미만은 1, 999 초과는 999")
    void pageIsClamped() {
        assertThat(ItemSearchCondition.parsePage("")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("2")).isEqualTo(2);
        assertThat(ItemSearchCondition.parsePage("0")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("-3")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage(" 2")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("abc")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("1000")).isEqualTo(999);
        assertThat(ItemSearchCondition.parsePage("99999999999999999999")).isEqualTo(999);
    }

    @Test
    @DisplayName("정렬: 대소문자 · 공백 무시, level 은 dir=asc 일 때만 오름차순, 모르는 기준은 기본 정렬")
    void sortFollowsLegacySwitch() {
        assertThat(ItemSearchCondition.parseSort(" Level ", "asc"))
            .isEqualTo(Sort.by(Sort.Order.asc("level"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.parseSort("level", ""))
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.parseSort("title", "sideways"))
            .isEqualTo(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.parseSort("unit", "DESC"))
            .isEqualTo(Sort.by(Sort.Order.desc("unitCode"), Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.parseSort("created", "desc"))
            .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.parseSort("id", "desc")).isEqualTo(Sort.by(Sort.Order.desc("id")));
        assertThat(ItemSearchCondition.parseSort("nope", "asc"))
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("tag[] 로 오면 첫 값, 같은 이름이 반복되면 마지막 값(PHP 규칙)")
    void arrayParamUsesFirstAndRepeatedUsesLast() {
        assertThat(condition(Map.of("tag[]", List.of("도형", "계산"))).getTag()).isEqualTo("도형");
        assertThat(condition(Map.of("tag", List.of("도형", "계산"))).getTag()).isEqualTo("계산");
    }

    @Test
    @DisplayName("단원 코드는 trim 만 하고 대소문자를 바꾸지 않는다")
    void unitCodeKeepsCase() {
        assertThat(condition(Map.of("unit", List.of(" m5-1 "))).getUnitCode()).isEqualTo("m5-1");
    }

    @Test
    @DisplayName("태그는 50자로 자른다")
    void tagIsCutAt50() {
        assertThat(ItemSearchCondition.parseTag("가".repeat(51))).isEqualTo("가".repeat(50));
    }
}

package com.example.item;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 문항 검색 WHERE 절. 조건은 모두 AND 로 잇는다(CX-01).
 * 대소문자 구분 · 정렬 순서는 DB 콜레이션(utf8mb4_unicode_ci)에 맡기고 여기서는 바꾸지 않는다.
 */
final class ItemSearchSpecs {

    private ItemSearchSpecs() {
    }

    static Specification<ItemSearchRow> of(ItemSearchCondition condition) {
        List<Specification<ItemSearchRow>> specs = new ArrayList<>();
        if (condition.getKeyword() != null) {
            specs.add(keyword(condition.getKeyword()));
        }
        if (condition.getUnitCode() != null) {
            specs.add(unitCode(condition.getUnitCode()));
        }
        specs.add(level(condition.getLevel()));
        if (condition.getTag() != null) {
            specs.add(tag(condition.getTag()));
        }
        return Specification.allOf(specs);
    }

    /** CX-02 · CX-03: 제목 또는 지문 부분 일치. {@code %} · {@code _} 는 와일드카드 그대로(ESCAPE 없음). */
    static Specification<ItemSearchRow> keyword(String keyword) {
        String like = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(cb.like(root.get("title"), like), cb.like(root.get("stem"), like));
    }

    /** CX-04: 단원 코드 정확 일치. 없는 코드도 그대로 조회한다(0건). */
    static Specification<ItemSearchRow> unitCode(String unitCode) {
        return (root, query, cb) -> cb.equal(root.get("unitCode"), unitCode);
    }

    /**
     * CX-05 · CX-06: null 이면 {@code level < 5}(난이도 5 제외), 아니면 {@code level = 값}.
     * INT 컬럼 범위를 벗어난 값은 어떤 행과도 같지 않으므로 항상 거짓으로 둔다.
     */
    static Specification<ItemSearchRow> level(Long level) {
        if (level == null) {
            return (root, query, cb) -> cb.lessThan(root.get("level"), 5);
        }
        if (level < Integer.MIN_VALUE || level > Integer.MAX_VALUE) {
            return (root, query, cb) -> cb.disjunction();
        }
        int value = level.intValue();
        return (root, query, cb) -> cb.equal(root.get("level"), value);
    }

    /** CX-07: 태그 이름 정확 일치 — item_tag · tag 를 EXISTS 로 조인한다. */
    static Specification<ItemSearchRow> tag(String tagName) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<Item> item = sub.from(Item.class);
            Join<Item, Tag> tag = item.join("tags");
            sub.select(item.get("id"))
                .where(cb.equal(item.get("id"), root.get("id")), cb.equal(tag.get("name"), tagName));
            return cb.exists(sub);
        };
    }
}

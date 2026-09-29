package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

/**
 * 검색 조건 → SQL 슬라이스. H2 에서는 {@code v_item_public} 이 뷰가 아니라 테이블로 만들어지므로 행을 직접 넣는다.
 * 대소문자 무시 · 제목 정렬 순서는 MariaDB 콜레이션에 달려 있어 여기서 확인하지 않는다(동작 보존 테스트에서 본다).
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemSearchRowRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemSearchRowRepository itemSearchRowRepository;

    @BeforeEach
    void seed() {
        Unit fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        Item a = entityManager.persist(ItemFixtures.item(null, fraction, "분수 덧셈", 2, ItemStatus.ACTIVE, calc));
        Item b = entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word));
        Item c = entityManager.persist(ItemFixtures.item(null, fraction, "최상 문항", 5, ItemStatus.ACTIVE, word));
        Item d = entityManager.persist(ItemFixtures.item(null, fraction, "같은 난이도", 4, ItemStatus.ACTIVE));
        entityManager.flush();

        insertViewRow(a, "M5-1", "계산");
        insertViewRow(b, "M5-1", "계산,문장제");
        insertViewRow(c, "M5-2", "문장제");
        insertViewRow(d, "M5-2", null);
        entityManager.clear();
    }

    private void insertViewRow(Item item, String unitCode, String tagNames) {
        entityManager.getEntityManager().createNativeQuery("""
                insert into v_item_public (id, unit_code, title, stem, level, tag_names, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """)
            .setParameter(1, item.getId())
            .setParameter(2, unitCode)
            .setParameter(3, item.getTitle())
            .setParameter(4, item.getStem())
            .setParameter(5, item.getLevel())
            .setParameter(6, tagNames)
            .setParameter(7, item.getCreatedAt())
            .executeUpdate();
    }

    private Page<ItemSearchRow> search(Map<String, List<String>> params) {
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        return itemSearchRowRepository.findAll(ItemSearchSpecs.of(condition), condition.pageable());
    }

    @Test
    @DisplayName("조건 없음: 난이도 5 제외, level DESC · id ASC")
    void defaultExcludesLevel5() {
        Page<ItemSearchRow> page = search(Map.of());

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(ItemSearchRow::getTitle)
            .containsExactly("분수 문장제", "같은 난이도", "분수 덧셈");
    }

    @Test
    @DisplayName("level=5 를 명시하면 난이도 5 가 나온다")
    void explicitLevel5() {
        assertThat(search(Map.of("level", List.of("5"))).getContent())
            .extracting(ItemSearchRow::getTitle).containsExactly("최상 문항");
    }

    @Test
    @DisplayName("범위 밖 난이도 · INT 범위를 넘는 난이도는 0건")
    void outOfRangeLevelIsEmpty() {
        assertThat(search(Map.of("level", List.of("-1"))).getTotalElements()).isZero();
        assertThat(search(Map.of("level", List.of("99999999999999999999"))).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("키워드의 % 는 와일드카드로 동작한다")
    void percentIsWildcard() {
        assertThat(search(Map.of("q", List.of("분수%문장"))).getContent())
            .extracting(ItemSearchRow::getTitle).containsExactly("분수 문장제");
    }

    @Test
    @DisplayName("단원 + 태그는 AND, 태그는 이름 정확 일치(EXISTS)")
    void unitAndTagCombined() {
        assertThat(search(Map.of("unit", List.of("M5-1"), "tag", List.of("문장제"))).getContent())
            .extracting(ItemSearchRow::getTitle).containsExactly("분수 문장제");
        assertThat(search(Map.of("tag", List.of("문장"))).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("없는 단원 코드는 오류 없이 0건")
    void unknownUnitIsEmpty() {
        assertThat(search(Map.of("unit", List.of("Z9-99"))).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("마지막 페이지 다음: 건수는 있고 행은 없다")
    void pastLastPageKeepsCount() {
        Page<ItemSearchRow> page = search(Map.of("page", List.of("2")));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}

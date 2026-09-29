package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemSearchRowRepository itemSearchRowRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    @SuppressWarnings("unchecked")
    private static Specification<ItemSearchRow> anySpec() {
        return any(Specification.class);
    }

    @Test
    @DisplayName("search: 행은 id · title · unit · level · tags 로 바뀌고, 태그는 쉼표로 나뉜다")
    void searchMapsRows() {
        when(itemSearchRowRepository.findAll(anySpec(), any(Pageable.class))).thenReturn(new PageImpl<>(
            List.of(ItemSearchFixtures.row(18, "분수의 뺄셈 문장제", "M5-1", 4, "문장제,오답률높음"),
                ItemSearchFixtures.row(3, "태그 없는 문항", "M5-3", 1, null)),
            PageRequest.of(0, 20), 2));

        ItemSearchResponse response = itemSearchService.search(Map.of());

        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getRows()).extracting(ItemSearchRowResponse::getId).containsExactly(18, 3);
        assertThat(response.getRows().get(0).getUnit()).isEqualTo("M5-1");
        assertThat(response.getRows().get(0).getTags()).containsExactly("문장제", "오답률높음");
        assertThat(response.getRows().get(1).getTags()).isEmpty();
    }

    @Test
    @DisplayName("search: 전체 0건이면 레거시와 같은 안내 문구를 담는다(404 아님)")
    void searchWithNoResultHasMessage() {
        when(itemSearchRowRepository.findAll(anySpec(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ItemSearchResponse response = itemSearchService.search(Map.of("unit", List.of("Z9-99")));

        assertThat(response.getRows()).isEmpty();
        assertThat(response.getCount()).isZero();
        assertThat(response.getMessage()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("search: 건수가 있으면 해당 페이지 행이 없어도 안내 문구는 없다")
    void searchPastLastPageHasCountButNoMessage() {
        when(itemSearchRowRepository.findAll(anySpec(), eq(PageRequest.of(1, 20,
            ItemSearchCondition.parseSort("", ""))))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 20));

        ItemSearchResponse response = itemSearchService.search(Map.of("page", List.of("2")));

        assertThat(response.getRows()).isEmpty();
        assertThat(response.getCount()).isEqualTo(20);
        assertThat(response.getMessage()).isNull();
        verify(itemSearchRowRepository).findAll(anySpec(), eq(PageRequest.of(1, 20,
            ItemSearchCondition.parseSort("", ""))));
    }
}

package com.example.item;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code GET /api/items/search} 응답. 동작 보존 테스트의 정규화 모양 {rows, count, message} 와 같다.
 * {@code count} 는 페이지와 무관한 전체 건수이고, 전체 0건일 때만 {@code message} 가 있다(CX-13).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemSearchResponse {

    /** 레거시 {@code <p id="message">} 와 같은 문구. */
    static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    private final List<ItemSearchRowResponse> rows;
    private final long count;
    private final String message;

    static ItemSearchResponse of(List<ItemSearchRowResponse> rows, long count) {
        return ItemSearchResponse.builder()
            .rows(rows)
            .count(count)
            .message(count == 0 ? NO_RESULT_MESSAGE : null)
            .build();
    }
}

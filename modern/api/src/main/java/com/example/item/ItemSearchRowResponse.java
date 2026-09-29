package com.example.item;

import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 검색 결과 한 행. 동작 보존 테스트의 정규화 필드(id, title, unit, level, tags)만 둔다.
 * {@code tags} 는 뷰의 {@code tag_names}(태그 id 순, 쉼표 구분)를 나눈 것이고, NULL 이면 빈 목록이다(CX-14).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemSearchRowResponse {

    private final Integer id;
    private final String title;
    private final String unit;
    private final Integer level;
    private final List<String> tags;

    static ItemSearchRowResponse from(ItemSearchRow row) {
        return ItemSearchRowResponse.builder()
            .id(row.getId())
            .title(row.getTitle())
            .unit(row.getUnitCode())
            .level(row.getLevel())
            .tags(splitTags(row.getTagNames()))
            .build();
    }

    private static List<String> splitTags(String tagNames) {
        if (tagNames == null) {
            return List.of();
        }
        return Arrays.stream(tagNames.split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .toList();
    }
}

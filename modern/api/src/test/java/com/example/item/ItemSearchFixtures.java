package com.example.item;

import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 검색 읽기 모델(뷰 행) 생성 도우미. 엔티티가 읽기 전용이라 필드를 직접 채운다. */
final class ItemSearchFixtures {

    private ItemSearchFixtures() {
    }

    static ItemSearchRow row(int id, String title, String unitCode, int level, String tagNames) {
        ItemSearchRow row = BeanUtils.instantiateClass(ItemSearchRow.class);
        ReflectionTestUtils.setField(row, "id", id);
        ReflectionTestUtils.setField(row, "title", title);
        ReflectionTestUtils.setField(row, "unitCode", unitCode);
        ReflectionTestUtils.setField(row, "level", level);
        ReflectionTestUtils.setField(row, "tagNames", tagNames);
        ReflectionTestUtils.setField(row, "createdAt", ItemFixtures.SEED_TIME);
        return row;
    }
}

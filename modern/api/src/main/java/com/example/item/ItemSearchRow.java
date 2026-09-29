package com.example.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 문항 검색 전용 읽기 모델. 공개 문항 뷰 {@code v_item_public} 의 한 행(레거시 search.php 와 같은 조회 대상).
 * 뷰가 {@code status = 'A'} 만 걸러 주고, {@code tag_names} 는 태그 id 순으로 쉼표로 이어 붙인 문자열이다.
 */
@Entity
@Immutable
@Table(name = "v_item_public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemSearchRow {

    @Id
    private Integer id;

    @Column(name = "unit_code")
    private String unitCode;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String stem;

    private Integer level;

    @Column(name = "tag_names")
    private String tagNames;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

package com.example.item;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스 — 레거시 search.php 의 이관. 공개 문항 뷰만 조회하고(CX-14),
 * 목록과 같은 조건으로 전체 건수를 따로 센다(CX-13). 없는 단원 · 범위 밖 난이도도 오류가 아니라 0건이다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemSearchRowRepository itemSearchRowRepository;

    public ItemSearchService(ItemSearchRowRepository itemSearchRowRepository) {
        this.itemSearchRowRepository = itemSearchRowRepository;
    }

    /** 요청 파라미터(이름 → 값 목록) 그대로 받아 검색한다. */
    public ItemSearchResponse search(Map<String, List<String>> params) {
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        Specification<ItemSearchRow> spec = ItemSearchSpecs.of(condition);
        Page<ItemSearchRow> page = itemSearchRowRepository.findAll(spec, condition.pageable());
        List<ItemSearchRowResponse> rows = page.getContent().stream()
            .map(ItemSearchRowResponse::from)
            .toList();
        log.debug("item search page {} → {} rows of {}", condition.getPage(), rows.size(), page.getTotalElements());
        return ItemSearchResponse.of(rows, page.getTotalElements());
    }
}

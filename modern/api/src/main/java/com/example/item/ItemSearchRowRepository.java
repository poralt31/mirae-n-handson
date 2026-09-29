package com.example.item;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 공개 문항 뷰 검색. 조건은 {@link ItemSearchSpecs} 로 조립한다. */
public interface ItemSearchRowRepository
    extends JpaRepository<ItemSearchRow, Integer>, JpaSpecificationExecutor<ItemSearchRow> {
}

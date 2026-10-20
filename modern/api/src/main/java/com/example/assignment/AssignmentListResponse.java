package com.example.assignment;

import com.example.assignment.AssignmentRepository.AssignmentListRow;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code GET /api/assignments} 응답. 동작 보존 테스트의 정규화 모양 {items, count} 와 같다.
 * 레거시 표 머리글 과제명 · 마감 · 배포 학급 수는 정규화에서 col2 · col4 · col6 이 되므로 JSON 이름도 그대로 맞춘다.
 * 마감 · 상태는 레거시 화면(assignments.html)의 표시 문자열이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AssignmentListResponse {

    private final List<Row> items;
    private final int count;

    static AssignmentListResponse of(List<Row> items) {
        return AssignmentListResponse.builder()
            .items(items)
            .count(items.size())
            .build();
    }

    /** 과제 목록 한 행. */
    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Row {

        private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private final Integer id;

        @JsonProperty("col2")
        private final String title;

        private final String unit;

        @JsonProperty("col4")
        private final String dueAt;

        private final String status;

        @JsonProperty("col6")
        private final long classCount;

        static Row from(AssignmentListRow row) {
            return Row.builder()
                .id(row.getId())
                .title(row.getTitle())
                .unit(row.getUnitCode())
                .dueAt(row.getDueAt() == null ? "" : row.getDueAt().format(DUE_FORMAT))
                .status(statusLabel(row.getStatus()))
                .classCount(row.getClassCount() == null ? 0 : row.getClassCount())
                .build();
        }

        /** 레거시 화면의 상태 표시. 모르는 코드는 코드 그대로 보여 준다. */
        private static String statusLabel(String status) {
            return switch (status) {
                case "O" -> "진행중";
                case "X" -> "연장";
                case "C" -> "종료";
                case "R" -> "검수중";
                default -> status;
            };
        }
    }
}

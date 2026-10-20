package com.example.assignment;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {

    /**
     * 과제 목록 — 레거시 {@code AssignmentDao.findAllForList()} 와 같은 조건 · 정렬.
     * 삭제(D) 제외, 마감 · ID 오름차순. 배포 학급 수는 같은 학급에 재배포한 이력을 한 번만 센다.
     */
    @Query("""
        select a.id as id, a.title as title, u.code as unitCode, a.dueAt as dueAt, a.status as status,
               (select count(distinct d.classRoom.id) from Distribution d where d.assignment = a) as classCount
        from Assignment a left join a.unit u
        where a.status <> 'D'
        order by a.dueAt asc, a.id asc
        """)
    List<AssignmentListRow> findAllForList();

    /** {@link #findAllForList()} 의 한 행. */
    interface AssignmentListRow {
        Integer getId();

        String getTitle();

        String getUnitCode();

        LocalDateTime getDueAt();

        String getStatus();

        Long getClassCount();
    }
}

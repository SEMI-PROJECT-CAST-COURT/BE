package com.verdict.castcourt.domain.work.repository;

import com.verdict.castcourt.domain.work.dto.WorkListItem;
import com.verdict.castcourt.domain.work.dto.WorkSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class WorkListRepository {
    private final EntityManager entityManager;
    public WorkListRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<WorkListItem> findWorks(Integer categoryId, String keyword, WorkSort sort, int page) {
        String filter = " FROM work w JOIN category c ON c.id = w.category_id"
                + " WHERE w.is_usage = TRUE";
        if (categoryId != null) filter += " AND w.category_id = :categoryId";
        if (!keyword.isEmpty()) filter += " AND LOCATE(:keyword, w.title) > 0";
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*)" + filter);
        bind(countQuery, categoryId, keyword);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query query = entityManager.createNativeQuery(
                "SELECT w.id, c.name, w.title, w.poster,"
                + " (SELECT COUNT(*) FROM work_case wc WHERE wc.work_id = w.id AND wc.is_usage = TRUE)"
                + filter + " ORDER BY " + sort.orderBy());
        bind(query, categoryId, keyword);
        query.setFirstResult((page - 1) * 8);
        query.setMaxResults(8);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<WorkListItem> items = rows.stream().map(row -> new WorkListItem(
                ((Number) row[0]).intValue(), (String) row[1], (String) row[2],
                (String) row[3], ((Number) row[4]).longValue())).toList();
        return new PageImpl<>(items, PageRequest.of(page - 1, 8), total);
    }

    private void bind(Query query, Integer categoryId, String keyword) {
        if (categoryId != null) query.setParameter("categoryId", categoryId);
        if (!keyword.isEmpty()) query.setParameter("keyword", keyword);
    }
}
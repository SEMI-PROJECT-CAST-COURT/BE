package com.verdict.castcourt.domain.work.repository;
import com.verdict.castcourt.domain.work.entity.Work;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRepository extends JpaRepository<Work, Integer> {
    boolean existsByTitle(String title);
    boolean existsByTitleAndIdNot(String title, Integer id);
    @org.springframework.data.jpa.repository.Query(
            "select w from Work w join fetch w.category where w.id = :id and w.isUsage = true")
    java.util.Optional<Work> findActiveById(@org.springframework.data.repository.query.Param("id") Integer id);
}

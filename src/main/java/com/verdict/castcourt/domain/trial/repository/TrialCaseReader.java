package com.verdict.castcourt.domain.trial.repository;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;

import java.util.Optional;

/* 재판에 필요한 사건 · 카드 · 형벌표 읽기
 *  재판 쪽은 이 인터페이스만 알고, 실제로 어디서 읽는지는 구현 클래스가 정한다
 *  TODO(연결): 사건 · 카드 · 형벌표 엔티티가 확정되면 구현을 엔티티 조회로 바꿀지 검토 (권도연)
 * */
public interface TrialCaseReader {

    /* 삭제되지 않은 사건이면 스냅샷, 없거나 삭제됐으면 빈 Optional */
    Optional<CaseSnapshot> findSnapshot(int caseId);
}
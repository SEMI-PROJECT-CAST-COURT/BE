package com.verdict.castcourt.domain.workcase.service;

import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseCreateRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.*;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseSummaryResponseDTO.*;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseListResponseDTO.*;
import com.verdict.castcourt.domain.workcase.dto.projection.WorkCaseCardRow;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import com.verdict.castcourt.domain.workcase.repository.WorkCaseRepository;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseDetailResponseDTO.*;

import java.util.List;

@Service
@Transactional
public class WorkCaseService {

    private final WorkCaseRepository workCaseRepository;

    @Autowired
    public WorkCaseService(WorkCaseRepository workCaseRepository) {
        this.workCaseRepository = workCaseRepository;
    }

    /* 사건 목록 조회 */
    @Transactional(readOnly = true)
    public WorkCaseListResponseDTO getWorkCaseList(int workId, int userId) {

        // 작품 id로 사건 목록 조회
        List<WorkCaseEntity> workCases = workCaseRepository.getWorkCasesByWorkId(workId).stream()
                .filter(WorkCaseEntity::isUsage)      // 삭제된 사건 제외
                .toList();

        List<WorkCaseSummaryResponseDTO> cases = workCases.stream()
                .map(workCase -> WorkCaseSummaryResponseDTO.of(
                        workCase,
                        // TODO: 근거 카드 구현 후, WeightSumResponse.toDifficulty()로 교체
                        new DifficultyResponseDTO(Difficulty.NORMAL, Difficulty.NORMAL),
                        // TODO: 재판(trial) 구현 후 내 판결 조회로 교체
                        new MyVerdictsResponse(null, null)
                ))
                .toList();

        // 해당 작품의 완료된 사건 개수 조회
        int done = workCaseRepository.countDoneWorkCases(workId, userId);

        return new WorkCaseListResponseDTO(
                new ProgressResponse(done, cases.size()),
                cases
        );
    }

    /* 사건 불러오기 - 사건 수정 화면에서 사용 */
    @Transactional(readOnly = true)
    public WorkCaseDetailResponseDTO getWorkCase(int caseId) {


        // 사건 조회
        WorkCaseEntity workCase = workCaseRepository.findById(caseId)
                .filter(WorkCaseEntity::isUsage)      // 삭제된 사건 제외
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        // 해당 사건의 근거 카드 조회
        List<CardResponse> cards = toCardResponse(workCaseRepository.findCardsByCaseId(caseId));

        return WorkCaseDetailResponseDTO.of(
                workCase,
                cards,
                workCaseRepository.findPenaltiesByCaseId(caseId).stream()
                        .map(row -> new PenaltyResponse(row.getTier(), row.getSentence()))
                        .toList()   // 해당 사건의 패널티 목록 조회
        );
    }

    /* 재판 준비 */
    @Transactional(readOnly = true)
    public WorkCasePrepareResponseDTO getWorkCasePrepare(int caseId, int userId) {

        WorkCaseEntity workCase = workCaseRepository.findById(caseId)
                .filter(WorkCaseEntity::isUsage)      // 삭제된 사건 제외
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        // 재판 기록 존재 여부 확인
        boolean defRecorded = workCaseRepository.existsByUserIdAndCaseIdAndRole(
                caseId, userId, "DEFENSE"
        ) > 0;
        boolean proRecorded = workCaseRepository.existsByUserIdAndCaseIdAndRole(
                caseId, userId, "PROSECUTION"
        ) > 0;

        return WorkCasePrepareResponseDTO.of(
                workCase,
                // TODO: 근거 카드 구현 후 대체
                new DifficultyResponseDTO(Difficulty.NORMAL, Difficulty.NORMAL),
                defRecorded,
                proRecorded,
                // TODO: 패널티 구현 후 대체
                null
        );
    }

    /* 사건 등록 */
    public int saveWorkCase(WorkCaseCreateRequestDTO requestDTO) {
        
        // TODO: 작품 존재 여부 체크

        // TODO: 입력값 검증

        WorkCaseEntity workCase = dtoToEntity(requestDTO);

        WorkCaseEntity savedWorkCase = workCaseRepository.save(workCase);

        // TODO: 근거 카드 등록
        // TODO: 형벌표 등록

        return savedWorkCase.getId();
    }

    // 프로젝션 -> 응답용 CardResponse 변환
    // TODO: EvidenceCard 엔티티 생성 후, 엔티티 기반 변환으로 교체
    private List<CardResponse> toCardResponse(List<WorkCaseCardRow> rows) {

        return rows.stream()
                .map(row -> new CardResponse(
                        Side.valueOf(row.getSide()),
                        row.getTitle(),
                        row.getBody(),
                        row.getWeight()
                ))
                .toList();
    }

    private WorkCaseEntity dtoToEntity(WorkCaseCreateRequestDTO requestDTO) {

        return new WorkCaseEntity(
                requestDTO.workId(),
                requestDTO.question(),
                requestDTO.suspect(),
                requestDTO.victim(),
                requestDTO.summary(),
                requestDTO.progress(),
                true
        );
    }
}

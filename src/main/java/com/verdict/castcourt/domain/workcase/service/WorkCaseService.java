package com.verdict.castcourt.domain.workcase.service;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardUpdateDTO;
import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import com.verdict.castcourt.domain.evidencecard.service.EvidenceCardService;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.penaltytier.entity.PenaltyTierEntity;
import com.verdict.castcourt.domain.penaltytier.service.PenaltyTierService;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseUpdateDTO;
import com.verdict.castcourt.domain.workcase.dto.response.*;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseListResponseDTO.ProgressResponse;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseSummaryResponseDTO.MyVerdictsResponse;
import com.verdict.castcourt.domain.workcase.entity.WorkCaseEntity;
import com.verdict.castcourt.domain.workcase.repository.WorkCaseRepository;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.verdict.castcourt.domain.workcase.dto.response.WorkCaseSummaryResponseDTO.*;


import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkCaseService {

    private final WorkCaseRepository workCaseRepository;
    private final WorkRepository workRepository;
    private final PenaltyTierService penaltyTierService;
    private final EvidenceCardService evidenceCardService;

    @Autowired
    public WorkCaseService(WorkCaseRepository workCaseRepository,
                           WorkRepository workRepository,
                           PenaltyTierService penaltyTierService,
                           EvidenceCardService evidenceCardService) {
        this.workCaseRepository = workCaseRepository;
        this.workRepository = workRepository;
        this.penaltyTierService = penaltyTierService;
        this.evidenceCardService = evidenceCardService;
    }

    /* 사건 목록 조회 */
    @Transactional(readOnly = true)
    public WorkCaseListResponseDTO getWorkCaseList(int workId, int userId) {

        // 작품 id로 사건 목록 조회
        List<WorkCaseEntity> workCases = workCaseRepository.getWorkCasesByWorkId(workId).stream()
                .filter(WorkCaseEntity::isUsage)      // 삭제된 사건 제외
                .toList();

        // 각 사건들의 근거 카드 목록 조회 - caseId로 grouping하여 Map으로 반환
        // 사건 1개 : 카드 여러 장 → mapping으로 DTO 변환 후 List로 모으고, collectingAndThen으로 그 List를 EvidenceCardResponseDTO로 한 번 더 감쌈
        Map<Integer, EvidenceCardResponseDTO> cardsByCase = workCaseRepository.findEvidenceCardsByWorkId(workId).stream()
                .collect(Collectors.groupingBy(
                        card -> card.getWorkCase().getId(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(this::toEvidenceCardDTO, Collectors.toList()),
                                EvidenceCardResponseDTO::new
                        )
                ));

        // 각 사건들의 형벌표 조회
        // 사건 1개 : 형벌표 6단계 → 같은 사건의 형벌표를 mapping으로 DTO 변환 후 List로 모음 (꺼낼 때 순서대로 전부 사용)
        Map<Integer, List<PenaltyTierDTO>> penaltiesByCase =
                workCaseRepository.findPenaltiesByWorkId(workId).stream()
                        .collect(Collectors.groupingBy(
                                penalty -> penalty.getWorkCase().getId(),           // 사건 아이디
                                Collectors.mapping(this::toPenaltyTierDTO, Collectors.toList())   // 형벌표 목록
                        ));

        // 각 사건들의 재판 결과 조회
        // 사건 1개 : 역할별 재판 최대 1건 (UNIQUE(user_id, work_case_id, role)) → List가 아니라 toMap으로 역할을 key로 묶음 (꺼낼 때 역할로 바로 조회)
        // 같은 key가 두 번 들어오면 toMap은 IllegalStateException(Duplicate key)을 던지므로, 역할당 1건이 보장될 때만 사용
        Map<Integer, Map<Side, TrialEntity>> trialsByCase =
                workCaseRepository.findMyTrialsByWorkId(workId, userId).stream()
                        .collect(Collectors.groupingBy(
                                TrialEntity::getWorkCaseId,                                      // 사건별
                                Collectors.toMap(TrialEntity::getRole, trial -> trial)  // 역할별
                        ));

        List<WorkCaseSummaryResponseDTO> cases = workCases.stream()
                .map(workCase -> {
                    // case 마다 근거카드 리스트 꺼내서 난이도 계산
                    EvidenceCardResponseDTO cards = cardsByCase.get(workCase.getId());
                    DifficultyResponseDTO difficulty = (cards == null)
                            ? new DifficultyResponseDTO(Difficulty.NORMAL, Difficulty.NORMAL)   // 근거 카드 없는 사건
                            : DifficultyResponseDTO.of(cards.getDefenseTotalWeight(), cards.getProsecutionTotalWeight());

                    // 특정 사건의 재편 결과를 역할별로 묶음
                    Map<Side, TrialEntity> mine = trialsByCase.getOrDefault(workCase.getId(), Map.of());
                    
                    return WorkCaseSummaryResponseDTO.of(
                            workCase,
                            difficulty,
                            penaltiesByCase.getOrDefault(workCase.getId(), List.of()),
                            new MyVerdictsResponse(
                                    toVerdictResponse(mine.get(Side.DEFENSE)),      // 변호사 역할 결과 (티어, 형벌 내용)
                                    toVerdictResponse(mine.get(Side.PROSECUTION))   // 검사 역할 결과   (티어, 형벌 내용)
                            )
                    );
                })
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

        return WorkCaseDetailResponseDTO.of(
                workCase,
                evidenceCardService.findEvidenceCardsByWorkCaseId(caseId),   // 해당 사건의 근거 카드 목록 조회
                penaltyTierService.findPenaltyTierByWorkCaseId(caseId).stream() // 해당 사건의 패널티 목록 조회
                        .sorted(Comparator.comparingInt(PenaltyTierDTO::getTier))
                        .toList()
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

        // 근거 카드 조회
        EvidenceCardResponseDTO cards = evidenceCardService.findEvidenceCardsByWorkCaseId(caseId);

        return WorkCasePrepareResponseDTO.of(
                workCase,
                DifficultyResponseDTO.of(   // 난이도 계산
                        cards.getDefenseTotalWeight(),
                        cards.getProsecutionTotalWeight()
                ),
                defRecorded,
                proRecorded,
                penaltyTierService.findPenaltyTierByWorkCaseId(caseId).stream() // 패널티 목록 조회
                        .sorted(Comparator.comparingInt(PenaltyTierDTO::getTier))
                        .toList()
        );
    }

    /* 사건 등록 */
    public int saveWorkCase(WorkCaseRequestDTO requestDTO) {

        // 사건 입력값 검증
        validateCase(requestDTO);

        // 작품 존재 여부 확인
        workRepository.findActiveById(requestDTO.workId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_NOT_FOUND));

        // 사건 등록
        WorkCaseEntity workCase = dtoToEntity(requestDTO);
        WorkCaseEntity savedWorkCase = workCaseRepository.save(workCase);

        // 근거 카드 등록
        evidenceCardService.saveEvidenceCard(savedWorkCase, requestDTO.cards());

        // 형벌표 등록
        penaltyTierService.savePenaltyTier(savedWorkCase, requestDTO.penalties());

        return savedWorkCase.getId();
    }

    /* 사건 수정 */
    public void updateWorkCase(int caseId, WorkCaseUpdateDTO requestDTO) {

        validateCase(requestDTO);
        
        // 사건 존재 여부 확인
        WorkCaseEntity workCase = workCaseRepository.findById(caseId)
                .filter(WorkCaseEntity::isUsage)
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        workCase.update(
                requestDTO.question(), requestDTO.suspect(),
                requestDTO.victim(), requestDTO.summary(), requestDTO.progress()
        );

        // 삭제할 근거카드 목록 - Integer 배열
        evidenceCardService.deleteEvidenceCards(caseId, requestDTO.deletedCardIds());
        // 수정할 근거카드 목록 - cards
        evidenceCardService.updateEvidenceCards(caseId, requestDTO.cards());
        // 형벌표 수정
        penaltyTierService.updatePenaltyTiers(caseId, requestDTO.penalties());
    }

    private EvidenceCardDTO toEvidenceCardDTO(EvidenceCardEntity card) {

        return new EvidenceCardDTO(
                card.getId(),
                card.getSide(),
                card.getTitle(),
                card.getBody(),
                card.getWeight()
        );
    }

    private PenaltyTierDTO toPenaltyTierDTO(PenaltyTierEntity penalty) {

        return new PenaltyTierDTO(
                penalty.getTier(),
                penalty.getSentence()
        );
    }

    private VerdictResponse toVerdictResponse(TrialEntity trial) {

        // 해당 역할로 판결 난 기록이 없는 경우
        if(trial == null) return null;

        // 재판 당시 스냅샷의 형벌표에서 해당 단계의 처분 문장 조회
        String sentence = trial.getCaseSnapshot().penalties().stream()
                .filter(penalty -> penalty.tier() == trial.getPenaltyTier())
                .map(CaseSnapshot.Penalty::sentence)
                .findFirst()
                .orElse(null);
        
        return new VerdictResponse(trial.getPenaltyTier(), sentence);
    }

    private WorkCaseEntity dtoToEntity(WorkCaseRequestDTO requestDTO) {

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

    // 사건 등록의 입력값 검증
    private void validateCase(WorkCaseRequestDTO requestDTO) {

        // 유효하지 않은 작품 id
        if(requestDTO.workId() <= 0)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null question 또는 길이 초과
        if(requestDTO.question() == null || requestDTO.question().isBlank() ||
                requestDTO.question().length() > 100)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null suspect 또는 길이 초과
        if(requestDTO.suspect() == null || requestDTO.suspect().isBlank() ||
                requestDTO.suspect().length() > 50)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null victim 또는 길이 초과
        if(requestDTO.victim() == null || requestDTO.victim().isBlank() ||
                requestDTO.victim().length() > 50)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null summary 또는 길이 초과
        if(requestDTO.summary() == null || requestDTO.summary().isBlank() ||
                requestDTO.summary().length() > 1000)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null progress
        if(requestDTO.progress() == null || requestDTO.progress().isBlank())
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // progress 길이 초과
        if(requestDTO.progress().length() > 1000)
            throw new BusinessException(ErrorCode.CASE_PROGRESS_TOO_LONG);
    }

    // 사건 수정의 입력값 검증
    private void validateCase(WorkCaseUpdateDTO requestDTO) {
        
        // null question 또는 길이 초과
        if (requestDTO.question() == null || requestDTO.question().isBlank() ||
                requestDTO.question().length() > 100)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null suspect 또는 길이 초과
        if (requestDTO.suspect() == null || requestDTO.suspect().isBlank() ||
                requestDTO.suspect().length() > 50)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null victim 또는 길이 초과
        if (requestDTO.victim() == null || requestDTO.victim().isBlank() ||
                requestDTO.victim().length() > 50)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null summary 또는 길이 초과
        if (requestDTO.summary() == null || requestDTO.summary().isBlank() ||
                requestDTO.summary().length() > 1000)
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // null progress
        if (requestDTO.progress() == null || requestDTO.progress().isBlank())
            throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        // progress 길이 초과
        if (requestDTO.progress().length() > 1000)
            throw new BusinessException(ErrorCode.CASE_PROGRESS_TOO_LONG);
        // 수정할 카드와 삭제할 카드가 겹치는 경우
        if (requestDTO.cards() != null && requestDTO.deletedCardIds() != null) {
            Set<Integer> deletedIds = new HashSet<>(requestDTO.deletedCardIds());
            boolean overlapped = requestDTO.cards().stream()
                    .map(EvidenceCardUpdateDTO::getId)
                    .anyMatch(deletedIds::contains);
            if (overlapped)
                throw new BusinessException(ErrorCode.CASE_INPUT_INVALID);
        }
    }
}

package com.verdict.castcourt.domain.workcase.service;

import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardDTO;
import com.verdict.castcourt.domain.evidencecard.dto.EvidenceCardResponseDTO;
import com.verdict.castcourt.domain.evidencecard.entity.EvidenceCardEntity;
import com.verdict.castcourt.domain.evidencecard.service.EvidenceCardService;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyTierDTO;
import com.verdict.castcourt.domain.penaltytier.entity.PenaltyTierEntity;
import com.verdict.castcourt.domain.penaltytier.service.PenaltyTierService;
import com.verdict.castcourt.domain.workcase.dto.request.WorkCaseRequestDTO;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkCaseService {

    private final WorkCaseRepository workCaseRepository;
    private final PenaltyTierService penaltyTierService;
    private final EvidenceCardService evidenceCardService;

    @Autowired
    public WorkCaseService(WorkCaseRepository workCaseRepository,
                           PenaltyTierService penaltyTierService,
                           EvidenceCardService evidenceCardService) {
        this.workCaseRepository = workCaseRepository;
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
        Map<Integer, EvidenceCardResponseDTO> cardsByCase = workCaseRepository.findEvidenceCardsByWorkId(workId).stream()
                .collect(Collectors.groupingBy(
                        card -> card.getWorkCase().getId(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(this::toEvidenceCardDTO, Collectors.toList()),
                                EvidenceCardResponseDTO::new
                        )
                ));

        // 각 사건들의 형벌표 조회
        Map<Integer, List<PenaltyTierDTO>> penaltiesByCase =
                workCaseRepository.findPenaltiesByWorkId(workId).stream()
                        .collect(Collectors.groupingBy(
                                penalty -> penalty.getWorkCase().getId(),           // 사건 아이디
                                Collectors.mapping(this::toPenaltyTierDTO, Collectors.toList())   // 형벌표 목록
                        ));
        
        List<WorkCaseSummaryResponseDTO> cases = workCases.stream()
                .map(workCase -> {
                    // case 마다 근거카드 리스트 꺼내서 난이도 계산
                    EvidenceCardResponseDTO cards = cardsByCase.get(workCase.getId());
                    DifficultyResponseDTO difficulty = (cards == null)
                            ? new DifficultyResponseDTO(Difficulty.NORMAL, Difficulty.NORMAL)   // 근거 카드 없는 사건
                            : DifficultyResponseDTO.of(cards.getDefenseTotalWeight(), cards.getProsecutionTotalWeight());

                    return WorkCaseSummaryResponseDTO.of(
                            workCase,
                            difficulty,
                            penaltiesByCase.getOrDefault(workCase.getId(), List.of()),
                            // TODO: 재판(trial) 구현 후 내 판결 조회로 교체
                            new MyVerdictsResponse(null, null)
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

        // 사건 등록
        WorkCaseEntity workCase = dtoToEntity(requestDTO);
        WorkCaseEntity savedWorkCase = workCaseRepository.save(workCase);

        // 근거 카드 등록
        evidenceCardService.saveEvidenceCard(savedWorkCase, requestDTO.cards());

        // 형벌표 등록
        penaltyTierService.savePenaltyTier(savedWorkCase, requestDTO.penalties());

        return savedWorkCase.getId();
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

    private void validateCase(WorkCaseRequestDTO requestDTO) {

        // TODO: 작품 존재 여부 체크

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
}

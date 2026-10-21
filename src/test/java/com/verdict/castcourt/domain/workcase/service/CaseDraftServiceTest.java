package com.verdict.castcourt.domain.workcase.service;

import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.workcase.ai.model.CaseDraftAi;
import com.verdict.castcourt.domain.workcase.dto.request.CaseDraftRequestDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftCandidateResponseDTO;
import com.verdict.castcourt.domain.workcase.dto.response.CaseDraftRewriteResponseDTO;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/* 사건 초안 서비스 : 작품 조회 → AI 호출 → 결과물만 돌려주기 */
@ExtendWith(MockitoExtension.class)
class CaseDraftServiceTest {

    private static final AiUsage USAGE = new AiUsage("test-model", 100, 10, LocalDateTime.now(), 5);
    private static final Work WORK = new Work(new Category("영화", 1), "기생충", "봉준호",
            LocalDate.of(2019, 5, 30), "작품 소개", "poster.png");

    @Mock
    private WorkRepository workRepository;

    @Mock
    private CaseDraftAi caseDraftAi;

    @InjectMocks
    private CaseDraftService caseDraftService;

    @Test
    @DisplayName("쟁점 후보 : 조회한 작품과 제외할 쟁점으로 AI 를 부르고 결과물을 돌려준다")
    void candidates() {
        List<String> exclude = List.of("A 는 유죄인가?");
        List<CaseDraftCandidateResponseDTO> candidates = List.of(
                new CaseDraftCandidateResponseDTO("쟁점", "피의자", "피해자", "개요", "경과"));
        when(workRepository.findActiveById(1)).thenReturn(Optional.of(WORK));
        when(caseDraftAi.candidates(WORK, exclude)).thenReturn(new AiResult<>(candidates, USAGE));

        assertEquals(candidates, caseDraftService.candidates(1, exclude));
    }

    @Test
    @DisplayName("다시 쓰기 : 조회한 작품과 요청으로 AI 를 부르고 결과물을 돌려준다")
    void rewrite() {
        CaseDraftRequestDTO request = new CaseDraftRequestDTO(
                "쟁점", "피의자", "피해자", "옛 개요", "옛 경과", "경과를 더 짧게");
        CaseDraftRewriteResponseDTO rewritten = new CaseDraftRewriteResponseDTO("새 개요", "새 경과");
        when(workRepository.findActiveById(1)).thenReturn(Optional.of(WORK));
        when(caseDraftAi.rewrite(WORK, request)).thenReturn(new AiResult<>(rewritten, USAGE));

        assertEquals(rewritten, caseDraftService.rewrite(1, request));
    }

    @Test
    @DisplayName("작품이 없으면 WORK_NOT_FOUND 이고 AI 는 부르지 않는다")
    void workNotFound() {
        when(workRepository.findActiveById(99)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> caseDraftService.candidates(99, List.of()));

        assertEquals(ErrorCode.WORK_NOT_FOUND, e.getErrorCode());
        verifyNoInteractions(caseDraftAi);
    }
}

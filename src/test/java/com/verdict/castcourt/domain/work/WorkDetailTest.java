package com.verdict.castcourt.domain.work;
import com.verdict.castcourt.global.exception.BusinessException;

import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.work.controller.WorkController;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.work.service.*;
import org.junit.jupiter.api.Test;



import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkDetailTest {
    @Test void returnsOnlySpecifiedWorkDetails() throws Exception {
        var repository = mock(WorkRepository.class);
        var date = LocalDate.of(2024,1,1);
        var work = new Work(new Category("영화",1), "작품", "작가", date,
                null, "/images/default-poster.svg");
        var id = Work.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(work, 1);
        when(repository.findActiveById(1)).thenReturn(Optional.of(work));
        var result = new WorkService(repository, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), mock(com.verdict.castcourt.domain.category.repository.CategoryRepository.class), mock(PosterStorage.class)).getWork(1);
        assertEquals(1, result.workId());
        assertEquals("작품", result.title());
        assertEquals("영화", result.category());
        assertEquals("작가", result.author());
        assertEquals(date, result.releasedOn());
        assertNull(result.summary());
        assertEquals("/images/default-poster.svg", result.posterUrl());
        assertEquals(7, result.getClass().getRecordComponents().length);
    }

    @Test void missingOrInactiveWorkReturns404() {
        var repository = mock(WorkRepository.class);
        when(repository.findActiveById(1)).thenReturn(Optional.empty());
        var error = assertThrows(BusinessException.class,
                () -> new WorkService(repository, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), mock(com.verdict.castcourt.domain.category.repository.CategoryRepository.class), mock(PosterStorage.class)).getWork(1));
        assertEquals(404, error.getErrorCode().getStatus().value());
        assertEquals("WORK_NOT_FOUND", error.getErrorCode().getCode());
        assertEquals("작품을 찾을 수 없습니다.", error.getMessage());
    }

    @Test
    void controllerDelegatesDetailLookup() {
        var service = mock(WorkService.class);
        var controller = new WorkController(service);
        controller.getWork(1);
        verify(service).getWork(1);
    }
}
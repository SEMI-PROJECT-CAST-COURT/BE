package com.verdict.castcourt.domain.work;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.work.controller.AdminWorkController;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.work.service.*;
import org.junit.jupiter.api.Test;


import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkDeleteTest {
    private final WorkRepository repository = mock(WorkRepository.class);
    private final WorkService service = new WorkService(repository, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), mock(com.verdict.castcourt.domain.category.repository.CategoryRepository.class), mock(PosterStorage.class));
    private Work work() {
        return new Work(new Category("영화",1),"작품","작가",LocalDate.of(2024,1,1),
                "소개","/images/default-poster.svg");
    }

    @Test void softDeletesWorkAndCasesWithoutDeletingRows() {
        var work=work();
        when(repository.findActiveById(1)).thenReturn(Optional.of(work));
        service.delete(1);
        assertFalse(work.isUsage());
        assertEquals("작품",work.getTitle());
        assertEquals("/images/default-poster.svg",work.getPoster());
        var order=inOrder(repository);
        order.verify(repository).hideCasesByWorkId(1);
        order.verify(repository).flush();
        verify(repository,never()).delete(any(Work.class));
        verify(repository,never()).deleteById(any());
    }

    @Test void missingOrAlreadyDeletedWorkReturns404() {
        when(repository.findActiveById(1)).thenReturn(Optional.empty());
        var error=assertThrows(BusinessException.class, () -> service.delete(1));
        assertEquals(404,error.getErrorCode().getStatus().value());
        assertEquals("WORK_NOT_FOUND",error.getErrorCode().getCode());
        verify(repository,never()).hideCasesByWorkId(any());
        verify(repository,never()).flush();
    }

    @Test void failedCaseUpdateDoesNotChangeWork() {
        var work=work();
        when(repository.findActiveById(1)).thenReturn(Optional.of(work));
        when(repository.hideCasesByWorkId(1)).thenThrow(new IllegalStateException("DB failure"));
        assertThrows(IllegalStateException.class, () -> service.delete(1));
        assertTrue(work.isUsage());
        verify(repository,never()).flush();
    }

    @Test
    void controllerReturnsEmpty204() {
        var service = mock(WorkService.class);
        var controller = new AdminWorkController(service);
        var response = controller.delete(1);
        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(service).delete(1);
    }
}
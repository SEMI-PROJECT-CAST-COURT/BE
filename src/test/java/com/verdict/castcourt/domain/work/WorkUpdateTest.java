package com.verdict.castcourt.domain.work;

import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import com.verdict.castcourt.domain.work.controller.AdminWorkController;
import com.verdict.castcourt.domain.work.dto.WorkUpdateRequest;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.work.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkUpdateTest {
    private final WorkRepository works = mock(WorkRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final PosterStorage posters = mock(PosterStorage.class);
    private final WorkUpdateService service = new WorkUpdateService(works, categories, posters);
    private final Category category = new Category("영화", 1);
    private final Work work = new Work(category, "기존", "원작자", LocalDate.of(2024,1,1),
            "기존 소개", "/images/default-poster.svg");

    private WorkUpdateRequest request(String title, String summary) {
        return new WorkUpdateRequest(2, title, "새 작가", LocalDate.of(2025,1,1), summary);
    }
    private void prepare() {
        when(works.findActiveById(1)).thenReturn(Optional.of(work));
        when(categories.findById(2)).thenReturn(Optional.of(category));
    }

    @Test void updatesAllFieldsAndPreservesPosterWhenOmitted() {
        prepare();
        service.update(1, request("수정", "가".repeat(100)), null);
        assertEquals("수정", work.getTitle());
        assertEquals("새 작가", work.getAuthor());
        assertEquals(LocalDate.of(2025,1,1), work.getReleasedOn());
        assertEquals("가".repeat(100), work.getSummary());
        assertSame(category, work.getCategory());
        assertEquals("/images/default-poster.svg", work.getPoster());
        verify(works).flush();
        verifyNoInteractions(posters);
    }

    @Test void emptyPosterKeepsExistingImageAndNullSummaryClearsIt() {
        prepare();
        service.update(1, request("기존", null), new MockMultipartFile("poster", new byte[0]));
        assertEquals("/images/default-poster.svg", work.getPoster());
        assertNull(work.getSummary());
        verify(works).existsByTitleAndIdNot("기존", 1);
        verifyNoInteractions(posters);
    }

    @Test void missingOrDeletedWorkReturns404() {
        when(works.findActiveById(1)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(WorkListException.class,
                () -> service.update(1, request("수정", null), null)).getStatus());
        verifyNoInteractions(posters);
    }

    @Test void invalidFieldsOrCategoryReturn400() {
        prepare();
        assertEquals("WORK_INPUT_INVALID", assertThrows(WorkListException.class,
                () -> service.update(1, request(" ", null), null)).getCode());
        assertThrows(WorkListException.class,
                () -> service.update(1, request("제목", "가".repeat(101)), null));
        when(categories.findById(2)).thenReturn(Optional.empty());
        assertThrows(WorkListException.class,
                () -> service.update(1, request("제목", null), null));
        verify(works, never()).flush();
        verifyNoInteractions(posters);
    }

    @Test void duplicateTitleReturns409BeforeUpload() {
        prepare();
        when(works.existsByTitleAndIdNot("중복",1)).thenReturn(true);
        assertEquals(409, assertThrows(WorkListException.class,
                () -> service.update(1, request("중복", null), null)).getStatus());
        verifyNoInteractions(posters);
    }

    @Test void replacesPosterAndCleansNewFileOnFailure() {
        prepare();
        var file = new MockMultipartFile("poster", "new.png", "image/png", new byte[]{1});
        when(posters.save(file)).thenReturn("/uploads/posters/new.png");
        doThrow(new IllegalStateException("DB failure")).when(works).flush();
        assertThrows(IllegalStateException.class,
                () -> service.update(1, request("수정", null), file));
        verify(posters).delete("/uploads/posters/new.png");
    }

    @Test void rollbackCleansOnlyNewPosterAndCommitKeepsIt() {
        for (int status : List.of(TransactionSynchronization.STATUS_ROLLED_BACK,
                                  TransactionSynchronization.STATUS_COMMITTED)) {
            prepare();
            var file = new MockMultipartFile("poster", new byte[]{1});
            when(posters.save(file)).thenReturn("/uploads/posters/new.png");
            TransactionSynchronizationManager.initSynchronization();
            try {
                service.update(1, request("수정", null), file);
                var callbacks = TransactionSynchronizationManager.getSynchronizations();
                assertEquals(1, callbacks.size());
                callbacks.getFirst().afterCompletion(status);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
        verify(posters, times(1)).delete("/uploads/posters/new.png");
        verify(posters, never()).delete("/images/default-poster.svg");
    }

    @Test void controllerReturnsEmpty200AndChecksRoles() {
        var updates = mock(WorkUpdateService.class);
        var controller = new AdminWorkController(mock(WorkCreateService.class), updates);
        assertEquals(401, assertThrows(WorkListException.class,
                () -> controller.update(1, request("수정", null), null, null)).getStatus());
        var user = new UsernamePasswordAuthenticationToken("user", "",
                List.of(new SimpleGrantedAuthority("USER")));
        assertEquals(403, assertThrows(WorkListException.class,
                () -> controller.update(1, request("수정", null), null, user)).getStatus());
        for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
            var admin = new UsernamePasswordAuthenticationToken("admin", "",
                    List.of(new SimpleGrantedAuthority(role)));
            var response = controller.update(1, request("수정", null), null, admin);
            assertEquals(200, response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}

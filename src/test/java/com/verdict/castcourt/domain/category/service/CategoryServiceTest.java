package com.verdict.castcourt.domain.category.service;
import com.verdict.castcourt.global.exception.BusinessException;


import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryServiceTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository, mock(com.verdict.castcourt.domain.work.repository.WorkRepository.class));

    @Test
    void rejectsBlankAndLongNames() {
        for (String name : new String[]{null, "", "   "}) {
            assertThrows(IllegalArgumentException.class, () -> service.createCategory(name));
        }
        assertEquals("CATEGORY_NAME_TOO_LONG",
                assertThrows(BusinessException.class,
                        () -> service.createCategory("가".repeat(11))).getErrorCode().getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDuplicateName() {
        when(repository.existsByName("영화")).thenReturn(true);
        assertEquals("CATEGORY_NAME_DUPLICATE",
                assertThrows(BusinessException.class, () -> service.createCategory("영화")).getErrorCode().getCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void addsAfterNormalCategoriesAndTrimsName() {
        when(repository.findAll()).thenReturn(List.of(
                new Category("영화", 1), new Category("게임", 2), new Category("미분류", 999)));
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(call -> call.getArgument(0));
        service.createCategory("  판타지  ");
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals("판타지", saved.getValue().getName());
        assertEquals(3, saved.getValue().getSortOrder());
    }

    @Test
    void handlesConcurrentDuplicateInsert() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.saveAndFlush(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        assertEquals("CATEGORY_NAME_DUPLICATE",
                assertThrows(BusinessException.class, () -> service.createCategory("영화")).getErrorCode().getCode());
    }
}
package com.verdict.castcourt.domain.category.service;
import com.verdict.castcourt.global.exception.BusinessException;


import com.verdict.castcourt.domain.category.controller.AdminCategoryController;
import com.verdict.castcourt.domain.category.dto.CategoryRenameRequest;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;


import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryRenameTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository, mock(com.verdict.castcourt.domain.work.repository.WorkRepository.class));

    @Test
    void renamesAndKeepsOrder() {
        Category category = new Category("영화", 2);
        when(repository.findById(1)).thenReturn(Optional.of(category));
        service.renameCategory(1, "SF");
        assertEquals("SF", category.getName());
        assertEquals(2, category.getSortOrder());
        verify(repository).flush();
    }

    @Test
    void missingCategoryReturns404() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.renameCategory(1, "SF"));
        assertEquals(404, error.getErrorCode().getStatus().value());
        assertEquals("CATEGORY_NOT_FOUND", error.getErrorCode().getCode());
    }

    @Test
    void fixedCategoryCannotBeRenamed() {
        when(repository.findById(4)).thenReturn(Optional.of(new Category("미분류", 4)));
        assertEquals("FIXED_CATEGORY_IMMUTABLE", assertThrows(BusinessException.class,
                () -> service.renameCategory(4, "SF")).getErrorCode().getCode());
        verify(repository, never()).flush();
    }

    @Test
    void rejectsDuplicateAndLongAndBlankNames() {
        when(repository.findById(1)).thenReturn(Optional.of(new Category("영화", 1)));
        when(repository.existsByNameAndIdNot("소설", 1)).thenReturn(true);
        assertEquals("CATEGORY_NAME_DUPLICATE", assertThrows(BusinessException.class,
                () -> service.renameCategory(1, "소설")).getErrorCode().getCode());
        assertEquals("CATEGORY_NAME_TOO_LONG", assertThrows(BusinessException.class,
                () -> service.renameCategory(1, "가".repeat(11))).getErrorCode().getCode());
        assertThrows(IllegalArgumentException.class, () -> service.renameCategory(1, " "));
    }

    @Test
    void unchangedNameIsAllowed() {
        when(repository.findById(1)).thenReturn(Optional.of(new Category("영화", 1)));
        assertDoesNotThrow(() -> service.renameCategory(1, "영화"));
        verify(repository).existsByNameAndIdNot("영화", 1);
    }

    @Test
    void controllerReturnsEmpty200() {
        var service = mock(CategoryService.class);
        var controller = new AdminCategoryController(service);
        var data = new CategoryRenameRequest("SF");
        var response = controller.rename(1, data);
        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(service).renameCategory(1, "SF");
    }
}
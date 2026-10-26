package com.verdict.castcourt.domain.category.service;
import com.verdict.castcourt.global.exception.BusinessException;

import com.verdict.castcourt.domain.category.controller.*;
import com.verdict.castcourt.domain.category.dto.CategoryOrderRequest;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;


import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryOrderTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository, mock(com.verdict.castcourt.domain.work.repository.WorkRepository.class));

    private Category category(int id, String name, int order) throws Exception {
        var category = new Category(name, order);
        var field = Category.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(category, id);
        return category;
    }

    @Test void savesArrayOrderAndKeepsFixedLast() throws Exception {
        var first = category(1,"영화",1);
        var second = category(2,"게임",2);
        var fixed = category(4,"미분류",999);
        when(repository.findAll()).thenReturn(List.of(first,second,fixed));
        service.reorderCategories(List.of(2,1));
        assertEquals(2,first.getSortOrder());
        assertEquals(1,second.getSortOrder());
        assertEquals(3,fixed.getSortOrder());
        verify(repository).flush();
    }

    @Test void rejectsDuplicateMissingUnknownAndFixedIdsWithoutMutation() throws Exception {
        var first = category(1,"영화",1);
        var second = category(2,"게임",2);
        var fixed = category(4,"미분류",999);
        when(repository.findAll()).thenReturn(List.of(first,second,fixed));
        for (var ids : List.of(List.of(1,1), List.of(1), List.of(1,3),
                List.of(1,2,4), List.<Integer>of())) {
            var error = assertThrows(BusinessException.class, () -> service.reorderCategories(ids));
            assertEquals(400,error.getErrorCode().getStatus().value());
            assertEquals("INVALID_CATEGORY_ORDER",error.getErrorCode().getCode());
        }
        assertEquals(1,first.getSortOrder());
        assertEquals(2,second.getSortOrder());
        assertEquals(999,fixed.getSortOrder());
        verify(repository,never()).flush();
    }

    @Test void rejectsNullListAndNullId() {
        assertThrows(BusinessException.class, () -> service.reorderCategories(null));
        assertThrows(BusinessException.class, () -> service.reorderCategories(Arrays.asList(1,null)));
        verifyNoInteractions(repository);
    }

    @Test void emptyListAllowedWhenOnlyFixedCategoryExists() throws Exception {
        var fixed = category(9,"미분류",9);
        when(repository.findAll()).thenReturn(List.of(fixed));
        service.reorderCategories(List.of());
        assertEquals(1,fixed.getSortOrder());
        verify(repository).flush();
    }

    @Test
    void controllerReturnsEmpty200() {
        var service = mock(CategoryService.class);
        var controller = new AdminCategoryController(service);
        var data = new CategoryOrderRequest(List.of(2,1));
        var response = controller.reorder(data);
        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(service).reorderCategories(List.of(2,1));
    }
}
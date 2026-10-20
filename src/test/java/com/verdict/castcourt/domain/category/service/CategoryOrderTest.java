package com.verdict.castcourt.domain.category.service;

import com.verdict.castcourt.domain.category.controller.*;
import com.verdict.castcourt.domain.category.dto.CategoryOrderRequest;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryOrderTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository);

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
            var error = assertThrows(CategoryApiException.class, () -> service.reorderCategories(ids));
            assertEquals(400,error.getStatus());
            assertEquals("INVALID_CATEGORY_ORDER",error.getCode());
        }
        assertEquals(1,first.getSortOrder());
        assertEquals(2,second.getSortOrder());
        assertEquals(999,fixed.getSortOrder());
        verify(repository,never()).flush();
    }

    @Test void rejectsNullListAndNullId() {
        assertThrows(CategoryApiException.class, () -> service.reorderCategories(null));
        assertThrows(CategoryApiException.class, () -> service.reorderCategories(Arrays.asList(1,null)));
        verifyNoInteractions(repository);
    }

    @Test void emptyListAllowedWhenOnlyFixedCategoryExists() throws Exception {
        var fixed = category(9,"미분류",9);
        when(repository.findAll()).thenReturn(List.of(fixed));
        service.reorderCategories(List.of());
        assertEquals(1,fixed.getSortOrder());
        verify(repository).flush();
    }

    @Test void controllerChecksAdminAndReturnsEmpty200() {
        var mocked = mock(CategoryService.class);
        var controller = new AdminCategoryController(mocked);
        var request = new CategoryOrderRequest(List.of(2,1));
        assertEquals(401,assertThrows(CategoryApiException.class,
                () -> controller.reorder(request,null)).getStatus());
        var user = new UsernamePasswordAuthenticationToken("user","",
                List.of(new SimpleGrantedAuthority("USER")));
        assertEquals(403,assertThrows(CategoryApiException.class,
                () -> controller.reorder(request,user)).getStatus());
        for (String role : List.of("ADMIN","SUPER_ADMIN")) {
            var auth = new UsernamePasswordAuthenticationToken("admin","",
                    List.of(new SimpleGrantedAuthority(role)));
            var response = controller.reorder(request,auth);
            assertEquals(200,response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}
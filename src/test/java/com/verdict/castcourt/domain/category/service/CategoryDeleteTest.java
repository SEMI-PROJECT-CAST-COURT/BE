package com.verdict.castcourt.domain.category.service;
import com.verdict.castcourt.domain.category.controller.*;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryDeleteTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final WorkRepository works = mock(WorkRepository.class);
    private final CategoryDeleteService service = new CategoryDeleteService(categories,works);

    @Test void movesWorksBeforePhysicalDeletion() {
        var category = new Category("영화",1);
        var fixed = new Category("미분류",4);
        when(categories.findById(1)).thenReturn(Optional.of(category));
        when(categories.findByName("미분류")).thenReturn(Optional.of(fixed));
        service.delete(1);
        var order = inOrder(works,categories);
        order.verify(works).moveToCategory(1,fixed);
        order.verify(categories).delete(category);
        order.verify(categories).flush();
    }

    @Test void rejectsFixedCategory() {
        when(categories.findById(4)).thenReturn(Optional.of(new Category("미분류",4)));
        var error = assertThrows(CategoryApiException.class, () -> service.delete(4));
        assertEquals(400,error.getStatus());
        assertEquals("FIXED_CATEGORY_DELETE_FORBIDDEN",error.getCode());
        verifyNoInteractions(works);
        verify(categories,never()).delete(any(Category.class));
    }

    @Test void rejectsMissingCategoryAndMissingFixedData() {
        when(categories.findById(1)).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(CategoryApiException.class, () -> service.delete(1)).getStatus());
        when(categories.findById(1)).thenReturn(Optional.of(new Category("영화",1)));
        when(categories.findByName("미분류")).thenReturn(Optional.empty());
        assertEquals(500,assertThrows(CategoryApiException.class, () -> service.delete(1)).getStatus());
        verifyNoInteractions(works);
    }

    @Test void doesNotDeleteCategoryWhenMoveFails() {
        var category = new Category("영화",1);
        var fixed = new Category("미분류",4);
        when(categories.findById(1)).thenReturn(Optional.of(category));
        when(categories.findByName("미분류")).thenReturn(Optional.of(fixed));
        when(works.moveToCategory(1,fixed)).thenThrow(new IllegalStateException("DB failure"));
        assertThrows(IllegalStateException.class, () -> service.delete(1));
        verify(categories,never()).delete(any(Category.class));
    }

    @Test void controllerChecksRolesAndReturnsEmpty204() {
        var mocked = mock(CategoryDeleteService.class);
        var controller = new AdminCategoryDeleteController(mocked);
        assertEquals(401,assertThrows(CategoryApiException.class,
                () -> controller.delete(1,null)).getStatus());
        var user = new UsernamePasswordAuthenticationToken("user","",
                List.of(new SimpleGrantedAuthority("USER")));
        assertEquals(403,assertThrows(CategoryApiException.class,
                () -> controller.delete(1,user)).getStatus());
        for (String role : List.of("ADMIN","SUPER_ADMIN")) {
            var auth = new UsernamePasswordAuthenticationToken("admin","",
                    List.of(new SimpleGrantedAuthority(role)));
            var response = controller.delete(1,auth);
            assertEquals(204,response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}
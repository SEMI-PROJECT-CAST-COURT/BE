package com.verdict.castcourt.domain.category.service;

import com.verdict.castcourt.domain.category.controller.CategoryApiException;
import com.verdict.castcourt.domain.category.controller.AdminCategoryController;
import com.verdict.castcourt.domain.category.dto.CategoryRenameRequest;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryRenameTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository);

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
        CategoryApiException error = assertThrows(CategoryApiException.class,
                () -> service.renameCategory(1, "SF"));
        assertEquals(404, error.getStatus());
        assertEquals("CATEGORY_NOT_FOUND", error.getCode());
    }

    @Test
    void fixedCategoryCannotBeRenamed() {
        when(repository.findById(4)).thenReturn(Optional.of(new Category("미분류", 4)));
        assertEquals("FIXED_CATEGORY_IMMUTABLE", assertThrows(CategoryApiException.class,
                () -> service.renameCategory(4, "SF")).getCode());
        verify(repository, never()).flush();
    }

    @Test
    void rejectsDuplicateAndLongAndBlankNames() {
        when(repository.findById(1)).thenReturn(Optional.of(new Category("영화", 1)));
        when(repository.existsByNameAndIdNot("소설", 1)).thenReturn(true);
        assertEquals("CATEGORY_NAME_DUPLICATE", assertThrows(CategoryApiException.class,
                () -> service.renameCategory(1, "소설")).getCode());
        assertEquals("CATEGORY_NAME_TOO_LONG", assertThrows(CategoryApiException.class,
                () -> service.renameCategory(1, "가".repeat(11))).getCode());
        assertEquals("CATEGORY_NAME_INVALID", assertThrows(CategoryApiException.class,
                () -> service.renameCategory(1, " ")).getCode());
    }

    @Test
    void unchangedNameIsAllowed() {
        when(repository.findById(1)).thenReturn(Optional.of(new Category("영화", 1)));
        assertDoesNotThrow(() -> service.renameCategory(1, "영화"));
        verify(repository).existsByNameAndIdNot("영화", 1);
    }

    @Test
    void controllerChecksRolesAndReturnsEmpty200() {
        CategoryService mockedService = mock(CategoryService.class);
        AdminCategoryController controller = new AdminCategoryController(mockedService);
        var request = new CategoryRenameRequest("SF");
        assertEquals(401, assertThrows(CategoryApiException.class,
                () -> controller.rename(1, request, null)).getStatus());
        var user = new UsernamePasswordAuthenticationToken("user", "",
                List.of(new SimpleGrantedAuthority("USER")));
        assertEquals(403, assertThrows(CategoryApiException.class,
                () -> controller.rename(1, request, user)).getStatus());
        for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
            var admin = new UsernamePasswordAuthenticationToken("admin", "",
                    List.of(new SimpleGrantedAuthority(role)));
            var response = controller.rename(1, request, admin);
            assertEquals(200, response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}
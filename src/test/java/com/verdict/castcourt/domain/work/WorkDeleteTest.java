package com.verdict.castcourt.domain.work;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.work.controller.AdminWorkController;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.work.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkDeleteTest {
    private final WorkRepository repository = mock(WorkRepository.class);
    private final WorkDeleteService service = new WorkDeleteService(repository);
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
        var error=assertThrows(WorkListException.class, () -> service.delete(1));
        assertEquals(404,error.getStatus());
        assertEquals("WORK_NOT_FOUND",error.getCode());
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

    @Test void controllerChecksRolesAndReturnsEmpty204() {
        var deletes=mock(WorkDeleteService.class);
        var controller=new AdminWorkController(mock(WorkCreateService.class),
                mock(WorkUpdateService.class),deletes);
        assertEquals(401,assertThrows(WorkListException.class,
                () -> controller.delete(1,null)).getStatus());
        var user=new UsernamePasswordAuthenticationToken("user","",
                List.of(new SimpleGrantedAuthority("USER")));
        assertEquals(403,assertThrows(WorkListException.class,
                () -> controller.delete(1,user)).getStatus());
        for(String role:List.of("ADMIN","SUPER_ADMIN")) {
            var admin=new UsernamePasswordAuthenticationToken("admin","",
                    List.of(new SimpleGrantedAuthority(role)));
            var response=controller.delete(1,admin);
            assertEquals(204,response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}
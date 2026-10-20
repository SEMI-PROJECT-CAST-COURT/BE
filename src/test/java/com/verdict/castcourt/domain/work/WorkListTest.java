package com.verdict.castcourt.domain.work;

import com.verdict.castcourt.domain.work.controller.WorkController;
import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.repository.WorkListRepository;
import com.verdict.castcourt.domain.work.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkListTest {
    private final WorkListRepository repository = mock(WorkListRepository.class);
    private final WorkListService service = new WorkListService(repository);

    @Test
    void forwardsFiltersAndEachSort() {
        for (WorkSort sort : WorkSort.values()) {
            when(repository.findWorks(2, "샘플", sort, 2)).thenReturn(Page.empty());
            service.getWorks(2, " 샘플 ", sort.name(), 2);
            verify(repository).findWorks(2, "샘플", sort, 2);
        }
    }

    @Test
    void rejectsInvalidQueryWithoutAccessingDatabase() {
        assertThrows(WorkListException.class, () -> service.getWorks(null, null, "BAD", 1));
        assertThrows(WorkListException.class, () -> service.getWorks(null, null, "LATEST", 0));
        assertThrows(WorkListException.class, () -> service.getWorks(0, null, "LATEST", 1));
        assertThrows(WorkListException.class, () -> service.getWorks(null, null, "LATEST", Integer.MAX_VALUE));
        verifyNoInteractions(repository);
    }

    @Test
    void omittedFiltersQueryAllWorks() {
        when(repository.findWorks(null, "", WorkSort.LATEST, 1)).thenReturn(Page.empty());
        service.getWorks(null, null, "LATEST", 1);
        verify(repository).findWorks(null, "", WorkSort.LATEST, 1);
    }

    @Test
    void controllerReturnsContentAndPaginationAndRequiresAuthentication() {
        WorkListService mockedService = mock(WorkListService.class);
        WorkController controller = new WorkController(mockedService, mock(WorkDetailService.class));
        assertEquals("AUTHENTICATION_REQUIRED", assertThrows(WorkListException.class,
                () -> controller.getWorks(null, null, "LATEST", 1, null)).getCode());
        var item = new WorkListItem(1, "영화", "샘플", "/uploads/posters/test.jpg", 2);
        when(mockedService.getWorks(null, null, "LATEST", 1))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 8), 9));
        var auth = new UsernamePasswordAuthenticationToken("user", "",
                List.of(new SimpleGrantedAuthority("USER")));
        var result = controller.getWorks(null, null, "LATEST", 1, auth);
        assertEquals(List.of(item), result.content());
        assertEquals(9, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.page());
        assertEquals(8, result.size());
    }
}
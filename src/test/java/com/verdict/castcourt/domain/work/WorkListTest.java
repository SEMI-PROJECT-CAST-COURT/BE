package com.verdict.castcourt.domain.work;
import com.verdict.castcourt.global.exception.BusinessException;

import com.verdict.castcourt.domain.work.controller.WorkController;
import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.repository.WorkListRepository;
import com.verdict.castcourt.domain.work.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;


import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkListTest {
    private final WorkListRepository repository = mock(WorkListRepository.class);
    private final WorkService service = new WorkService(mock(com.verdict.castcourt.domain.work.repository.WorkRepository.class), repository, mock(com.verdict.castcourt.domain.category.repository.CategoryRepository.class), mock(PosterStorage.class));

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
        assertThrows(BusinessException.class, () -> service.getWorks(null, null, "BAD", 1));
        assertThrows(BusinessException.class, () -> service.getWorks(null, null, "LATEST", 0));
        assertThrows(BusinessException.class, () -> service.getWorks(0, null, "LATEST", 1));
        assertThrows(BusinessException.class, () -> service.getWorks(null, null, "LATEST", Integer.MAX_VALUE));
        verifyNoInteractions(repository);
    }

    @Test
    void omittedFiltersQueryAllWorks() {
        when(repository.findWorks(null, "", WorkSort.LATEST, 1)).thenReturn(Page.empty());
        service.getWorks(null, null, "LATEST", 1);
        verify(repository).findWorks(null, "", WorkSort.LATEST, 1);
    }

    @Test
    void controllerReturnsContentAndPagination() {
        WorkService mockedService = mock(WorkService.class);
        WorkController controller = new WorkController(mockedService);
        var item = new WorkListItem(1, "영화", "샘플", "/uploads/posters/test.jpg", 2);
        when(mockedService.getWorks(null, null, "LATEST", 1))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 8), 9));
        var result = controller.getWorks(null, null, "LATEST", 1);
        assertEquals(List.of(item), result.content());
        assertEquals(9, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.page());
        assertEquals(8, result.size());
    }
}
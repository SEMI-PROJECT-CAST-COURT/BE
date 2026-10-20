package com.verdict.castcourt.domain.work;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.domain.work.dto.WorkCreateRequest;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.work.service.*;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkCreateTest {
    @TempDir Path temp;
    private WorkCreateRequest request(String summary) {
        return new WorkCreateRequest(1, "작품", "작가", LocalDate.of(2024,1,1), summary);
    }

    @Test void storesRealPngAndDeletesIt() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB), "png", output);
        var storage = new PosterStorage(temp.toString());
        String url = storage.save(new MockMultipartFile("poster", "image.png", "image/png", output.toByteArray()));
        assertTrue(url.startsWith("/uploads/posters/"));
        assertTrue(Files.exists(temp.resolve(url.substring(url.lastIndexOf('/') + 1))));
        storage.delete(url);
        try (var paths = Files.list(temp)) { assertEquals(0, paths.count()); }
    }

    @Test void rejectsMissingFakeAndOversizedPoster() {
        var storage = new PosterStorage(temp.toString());
        assertEquals("WORK_INPUT_INVALID", assertThrows(BusinessException.class, () -> storage.save(null)).getErrorCode().getCode());
        assertEquals("POSTER_INVALID_FORMAT", assertThrows(BusinessException.class, () -> storage.save(
                new MockMultipartFile("poster", "fake.png", "image/png", new byte[]{1,2,3}))).getErrorCode().getCode());
        assertEquals("POSTER_TOO_LARGE", assertThrows(BusinessException.class, () -> storage.save(
                new MockMultipartFile("poster", "large.png", "image/png", new byte[100*1024+1]))).getErrorCode().getCode());
    }

    @Test void validatesSummaryAndCategoryBeforeSavingFile() {
        var works = mock(WorkRepository.class);
        var categories = mock(CategoryRepository.class);
        var storage = mock(PosterStorage.class);
        var service = new WorkService(works, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), categories, storage);
        assertEquals("WORK_INPUT_INVALID", assertThrows(BusinessException.class,
                () -> service.create(request("가".repeat(101)), null)).getErrorCode().getCode());
        assertThrows(BusinessException.class, () -> service.create(request(null), null));
        verifyNoInteractions(storage);
    }

    @Test void duplicateTitleIncludesDeletedWorks() {
        var works = mock(WorkRepository.class);
        var categories = mock(CategoryRepository.class);
        var storage = mock(PosterStorage.class);
        when(categories.findById(1)).thenReturn(Optional.of(new Category("영화",1)));
        when(works.existsByTitle("작품")).thenReturn(true);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> new WorkService(works, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), categories, storage).create(request(null),null)).getErrorCode().getStatus().value());
        verifyNoInteractions(storage);
    }

    @Test void cleansPosterOnDatabaseFailure() {
        var works = mock(WorkRepository.class);
        var categories = mock(CategoryRepository.class);
        var storage = mock(PosterStorage.class);
        when(categories.findById(1)).thenReturn(Optional.of(new Category("영화",1)));
        var poster = new MockMultipartFile("poster", "image.png", "image/png", new byte[]{1});
        when(storage.save(poster)).thenReturn("/uploads/posters/example.png");
        when(works.saveAndFlush(any(Work.class))).thenThrow(new IllegalStateException("failed"));
        assertThrows(IllegalStateException.class,
                () -> new WorkService(works, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), categories, storage).create(request("가".repeat(100)),poster));
        verify(storage).delete("/uploads/posters/example.png");
    }

    @Test void missingOrEmptyPosterUsesDefault() throws Exception {
        var works = mock(WorkRepository.class);
        var categories = mock(CategoryRepository.class);
        var storage = mock(PosterStorage.class);
        when(categories.findById(1)).thenReturn(Optional.of(new Category("영화", 1)));
        when(works.saveAndFlush(any(Work.class))).thenAnswer(call -> call.getArgument(0));
        var service = new WorkService(works, mock(com.verdict.castcourt.domain.work.repository.WorkListRepository.class), categories, storage);
        service.create(request(null), null);
        service.create(request(null), new MockMultipartFile("poster", new byte[0]));
        var captor = org.mockito.ArgumentCaptor.forClass(Work.class);
        verify(works, times(2)).saveAndFlush(captor.capture());
        var field = Work.class.getDeclaredField("poster");
        field.setAccessible(true);
        for (var work : captor.getAllValues()) {
            assertEquals("/images/default-poster.svg", field.get(work));
        }
        verifyNoInteractions(storage);
        assertNotNull(getClass().getResource("/static/images/default-poster.svg"));
    }}
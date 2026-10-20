package com.verdict.castcourt.domain.work.service;

import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Component
public class PosterStorage {
    private final Path root;
    public PosterStorage(@Value("${app.poster-directory:uploads/posters}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalidInput();
        if (file.getSize() > 100L * 1024)
            throw new BusinessException(ErrorCode.POSTER_TOO_LARGE);
        try {
            byte[] bytes = file.getBytes();
            String format;
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) throw invalidFormat();
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidFormat();
                var reader = readers.next();
                try {
                    format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png")) throw invalidFormat();
                    reader.setInput(input);
                    if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) throw invalidFormat();
                } finally { reader.dispose(); }
            }
            Files.createDirectories(root);
            String filename = UUID.randomUUID() + (format.equals("jpeg") ? ".jpg" : ".png");
            Files.write(root.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
            return "/uploads/posters/" + filename;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void delete(String url) {
        Path target = root.resolve(url.substring(url.lastIndexOf('/') + 1)).normalize();
        if (!target.getParent().equals(root)) throw new IllegalArgumentException("Invalid poster path");
        try { Files.deleteIfExists(target); }
        catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(PosterStorage.class).warn("포스터 정리 실패: {}", target, exception);
        }
    }

    private BusinessException invalidInput() {
        return new BusinessException(ErrorCode.WORK_INPUT_INVALID);
    }
    private BusinessException invalidFormat() {
        return new BusinessException(ErrorCode.POSTER_INVALID_FORMAT);
    }
}
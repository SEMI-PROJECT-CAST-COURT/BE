package com.verdict.castcourt.domain.work.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class PosterWebConfig implements WebMvcConfigurer {
    private final String location;
    public PosterWebConfig(@Value("${app.poster-directory:uploads/posters}") String directory) {
        location = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
    }
    @org.springframework.context.annotation.Bean
    public jakarta.servlet.MultipartConfigElement multipartConfigElement() {
        return new jakarta.servlet.MultipartConfigElement(
                "", 100L * 1024, 1024L * 1024, 0);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/posters/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
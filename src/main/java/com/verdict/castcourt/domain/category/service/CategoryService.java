package com.verdict.castcourt.domain.category.service;

import com.verdict.castcourt.domain.category.controller.CategoryApiException;
import com.verdict.castcourt.domain.category.dto.CategoryResponse;
import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::isFixed)
                        .thenComparingInt(Category::getSortOrder)
                        .thenComparing(Category::getId))
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public Integer createCategory(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new CategoryApiException(400, "CATEGORY_NAME_INVALID", "카테고리 이름을 입력해 주세요.");
        }
        String name = rawName.strip();
        if (name.codePointCount(0, name.length()) > 10) {
            throw new CategoryApiException(400, "CATEGORY_NAME_TOO_LONG", "카테고리 이름은 10자 이하여야 합니다.");
        }
        if (categoryRepository.existsByName(name)) {
            throw duplicateName();
        }
        int order = categoryRepository.findAll().stream()
                .filter(category -> !category.isFixed())
                .mapToInt(Category::getSortOrder).max().orElse(0) + 1;
        try {
            return categoryRepository.saveAndFlush(new Category(name, order)).getId();
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 후 다른 요청이 같은 이름을 등록한 경우도 동일하게 처리.
            throw duplicateName();
        }
    }

    @Transactional
    public void renameCategory(Integer categoryId, String rawName) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryApiException(
                        404, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
        if (category.isFixed()) {
            throw new CategoryApiException(
                    400, "FIXED_CATEGORY_IMMUTABLE", "미분류 카테고리는 수정할 수 없습니다.");
        }
        if (rawName == null || rawName.isBlank()) {
            throw new CategoryApiException(
                    400, "CATEGORY_NAME_INVALID", "카테고리 이름을 입력해 주세요.");
        }
        String name = rawName.strip();
        if (name.codePointCount(0, name.length()) > 10) {
            throw new CategoryApiException(
                    400, "CATEGORY_NAME_TOO_LONG", "카테고리 이름은 10자 이하여야 합니다.");
        }
        if (categoryRepository.existsByNameAndIdNot(name, categoryId)) {
            throw duplicateName();
        }
        category.rename(name);
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw duplicateName();
        }
    }

    private CategoryApiException duplicateName() {
        return new CategoryApiException(400, "CATEGORY_NAME_DUPLICATE", "이미 존재하는 카테고리 이름입니다.");
    }
}
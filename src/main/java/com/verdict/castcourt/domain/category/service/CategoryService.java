package com.verdict.castcourt.domain.category.service;

import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;


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
    private final com.verdict.castcourt.domain.work.repository.WorkRepository works;
    public CategoryService(CategoryRepository categoryRepository, com.verdict.castcourt.domain.work.repository.WorkRepository works) {
        this.categoryRepository = categoryRepository;
        this.works = works;
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
            throw new IllegalArgumentException("카테고리 이름을 입력해 주세요.");
        }
        String name = rawName.strip();
        if (name.codePointCount(0, name.length()) > 10) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_TOO_LONG);
        }
        if (categoryRepository.existsByName(name)) {
            throw duplicateName();
        }
        var categories = categoryRepository.findAll();
        int order = categories.stream()
                .filter(category -> !category.isFixed())
                .mapToInt(Category::getSortOrder).max().orElse(0) + 1;
        try {
            categories.stream().filter(Category::isFixed)
                    .forEach(category -> category.changeOrder(order + 1));
            return categoryRepository.saveAndFlush(new Category(name, order)).getId();
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 후 다른 요청이 같은 이름을 등록한 경우도 동일하게 처리.
            throw duplicateName();
        }
    }

    @Transactional
    public void renameCategory(Integer categoryId, String rawName) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.isFixed()) {
            throw new BusinessException(ErrorCode.FIXED_CATEGORY_IMMUTABLE);
        }
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("카테고리 이름을 입력해 주세요.");
        }
        String name = rawName.strip();
        if (name.codePointCount(0, name.length()) > 10) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_TOO_LONG);
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

    @Transactional
    public void reorderCategories(List<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidOrder();
        }
        var categories = categoryRepository.findAll();
        var normalCategories = categories.stream().filter(category -> !category.isFixed())
                .collect(java.util.stream.Collectors.toMap(Category::getId, category -> category));
        var requestedIds = new java.util.HashSet<>(categoryIds);
        if (requestedIds.size() != categoryIds.size()
                || !requestedIds.equals(normalCategories.keySet())) {
            throw invalidOrder();
        }
        for (int index = 0; index < categoryIds.size(); index++) {
            normalCategories.get(categoryIds.get(index)).changeOrder(index + 1);
        }
        // 미분류의 ID는 요청에 포함하지 않고 서버에서 마지막 순서로 유지.
        categories.stream().filter(Category::isFixed)
                .forEach(category -> category.changeOrder(categoryIds.size() + 1));
        categoryRepository.flush();
    }

    private BusinessException invalidOrder() {
        return new BusinessException(ErrorCode.INVALID_CATEGORY_ORDER);
    }

    private BusinessException duplicateName() {
        return new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
    }

    @Transactional
    public void delete(Integer categoryId) {
        var category = categoryRepository.findById(categoryId).orElseThrow(() ->
                new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.isFixed()) {
            throw new BusinessException(ErrorCode.FIXED_CATEGORY_DELETE_FORBIDDEN);
        }
        var fixed = categoryRepository.findByName("미분류").orElseThrow(() ->
                new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        // 소프트 삭제된 작품도 FK를 참조하므로 모두 이동한 뒤 실제 행을 삭제한다.
        works.moveToCategory(categoryId, fixed);
        categoryRepository.delete(category);
        categoryRepository.flush();
    }
}
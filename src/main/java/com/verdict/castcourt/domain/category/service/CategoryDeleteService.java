package com.verdict.castcourt.domain.category.service;

import com.verdict.castcourt.domain.category.controller.CategoryApiException;
import com.verdict.castcourt.domain.category.repository.CategoryRepository;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryDeleteService {
    private final CategoryRepository categories;
    private final WorkRepository works;

    public CategoryDeleteService(CategoryRepository categories, WorkRepository works) {
        this.categories = categories;
        this.works = works;
    }

    @Transactional
    public void delete(Integer categoryId) {
        var category = categories.findById(categoryId).orElseThrow(() ->
                new CategoryApiException(404, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
        if (category.isFixed()) {
            throw new CategoryApiException(400, "FIXED_CATEGORY_DELETE_FORBIDDEN",
                    "미분류 카테고리는 삭제할 수 없습니다.");
        }
        var fixed = categories.findByName("미분류").orElseThrow(() ->
                new CategoryApiException(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
        // 소프트 삭제된 작품도 FK를 참조하므로 모두 이동한 뒤 실제 행을 삭제한다.
        works.moveToCategory(categoryId, fixed);
        categories.delete(category);
        categories.flush();
    }
}
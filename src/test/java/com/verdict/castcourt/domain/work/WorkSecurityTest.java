package com.verdict.castcourt.domain.work;

import com.verdict.castcourt.domain.category.controller.*;
import com.verdict.castcourt.domain.category.service.CategoryService;
import com.verdict.castcourt.domain.work.controller.*;
import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.service.*;
import com.verdict.castcourt.global.exception.*;
import com.verdict.castcourt.global.exception.handler.GlobalExceptionHandler;
import com.verdict.castcourt.global.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WorkSecurityTest.Config.class)
@WebAppConfiguration
class WorkSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, WorkController.class, AdminWorkController.class,
            CategoryController.class, AdminCategoryController.class, WorkSummaryDraftController.class,
            GlobalExceptionHandler.class})
    static class Config {
        @Bean WorkService workService() { return mock(WorkService.class); }
        @Bean CategoryService categoryService() { return mock(CategoryService.class); }
        @Bean WorkSummaryDraftService draftService() { return mock(WorkSummaryDraftService.class); }
        @Bean JwtTokenProvider tokenProvider() { return mock(JwtTokenProvider.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean JwtAuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
            return new JwtAuthenticationEntryPoint(mapper);
        }
        @Bean JwtAccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
            return new JwtAccessDeniedHandler(mapper);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired WorkService workService;
    @Autowired CategoryService categoryService;
    @Autowired WorkSummaryDraftService draftService;
    @Autowired JwtTokenProvider tokenProvider;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(workService, categoryService, draftService, tokenProvider);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void categoriesArePublicButWorksRequireLogin() throws Exception {
        when(categoryService.getCategories()).thenReturn(List.of());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/works")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.businessCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test void ordinaryUsersCannotUseAnyAdminMutation() throws Exception {
        var user = user("user").authorities(new SimpleGrantedAuthority("USER"));
        mvc.perform(post("/api/admin/categories").with(user).contentType("application/json")
                .content("{\"name\":\"영화\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/categories/1").with(user).contentType("application/json")
                .content("{\"name\":\"SF\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/categories/order").with(user).contentType("application/json")
                .content("{\"categoryIds\":[1]}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/categories/1").with(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/works").with(user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/works/1").with(user)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/works/1").with(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/works/summary-draft").with(user).contentType("application/json")
                .content("{\"title\":\"작품\",\"author\":\"작가\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.businessCode").value("ACCESS_DENIED"));
        verifyNoInteractions(workService, categoryService, draftService);
    }

    @Test void adminAndSuperAdminCanMutateAndGenerateDraft() throws Exception {
        when(draftService.generate(any())).thenReturn(new WorkSummaryDraftResponse("작품 소개"));
        for (String authority : List.of("ADMIN", "SUPER_ADMIN")) {
            var admin = user("admin").authorities(new SimpleGrantedAuthority(authority));
            mvc.perform(put("/api/admin/categories/order").with(admin).contentType("application/json")
                    .content("{\"categoryIds\":[2,1]}")).andExpect(status().isOk()).andExpect(content().string(""));
            mvc.perform(delete("/api/admin/categories/1").with(admin))
                    .andExpect(status().isNoContent());
            mvc.perform(delete("/api/admin/works/1").with(admin))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/admin/works/summary-draft").with(admin).contentType("application/json")
                    .content("{\"title\":\"작품\",\"author\":\"작가\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("작품 소개"));
        }
    }

    @Test void businessErrorsUseSharedHandlerAndResponse() throws Exception {
        when(workService.getWork(1)).thenThrow(new BusinessException(ErrorCode.WORK_NOT_FOUND));
        mvc.perform(get("/api/works/1").with(user("user").authorities(new SimpleGrantedAuthority("USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.businessCode").value("WORK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.WORK_NOT_FOUND.getMessage()));
    }

    @Test void malformedRequestsUseSharedDomainCodes() throws Exception {
        var admin = user("admin").authorities(new SimpleGrantedAuthority("ADMIN"));
        mvc.perform(put("/api/admin/categories/order").with(admin).contentType("application/json")
                .content("{")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value("INVALID_CATEGORY_ORDER"));
        mvc.perform(post("/api/admin/works/summary-draft").with(admin).contentType("application/json")
                .content("{")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value("WORK_DRAFT_INPUT_INVALID"));
    }

    @Test void invalidAndExpiredJwtKeepSharedAuthenticationCodes() throws Exception {
        when(tokenProvider.parseAccessToken("invalid")).thenThrow(new io.jsonwebtoken.JwtException("invalid"));
        mvc.perform(get("/api/works").header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.businessCode").value("AUTH_TOKEN_INVALID"));
        when(tokenProvider.parseAccessToken("expired"))
                .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null,null,"expired"));
        mvc.perform(get("/api/works").header("Authorization","Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.businessCode").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test void adminCanRegisterAndUpdateMultipartWork() throws Exception {
        when(workService.create(any(), any())).thenReturn(1);
        var data = new org.springframework.mock.web.MockMultipartFile(
                "data", "", "application/json",
                "{\"categoryId\":1,\"title\":\"작품\",\"author\":\"작가\",\"releasedOn\":\"2024-01-01\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var admin = user("admin").authorities(new SimpleGrantedAuthority("ADMIN"));
        mvc.perform(multipart("/api/admin/works").file(data).with(admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workId").value(1));
        mvc.perform(multipart("/api/admin/works/1").file(data)
                .with(request -> { request.setMethod("PUT"); return request; }).with(admin))
                .andExpect(status().isOk()).andExpect(content().string(""));
    }
}
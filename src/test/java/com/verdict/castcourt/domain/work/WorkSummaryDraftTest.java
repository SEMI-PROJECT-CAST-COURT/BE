package com.verdict.castcourt.domain.work;
import com.verdict.castcourt.global.exception.BusinessException;

import com.verdict.castcourt.domain.work.controller.WorkSummaryDraftController;
import com.verdict.castcourt.domain.work.dto.*;
import com.verdict.castcourt.domain.work.service.*;
import com.verdict.castcourt.global.ai.*;
import com.verdict.castcourt.global.exception.*;
import org.junit.jupiter.api.Test;


import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkSummaryDraftTest {
    private final OpenAiClient client = mock(OpenAiClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkSummaryDraftService service = new WorkSummaryDraftService(client, mapper);
    private WorkSummaryDraftRequest request() { return new WorkSummaryDraftRequest("트루먼 쇼","앤드루 니콜"); }

    @Test void returnsValidatedSummaryAsString() {
        when(client.createJson(anyString(),anyString(),anyInt(),anyString(),anyMap()))
                .thenReturn(new AiResult<>(mapper.readTree("{\"summary\":\" 평범한 일상 속 비밀을 발견하는 한 남자의 이야기 \"}"),null));
        assertEquals("평범한 일상 속 비밀을 발견하는 한 남자의 이야기",service.generate(request()).summary());
        verify(client).createJson(anyString(),contains("트루먼 쇼"),eq(800),eq("work_summary_draft"),anyMap());
    }

    @Test void rejectsInvalidInputWithoutCallingAi() {
        for (var request : List.of(
                new WorkSummaryDraftRequest(null,"작가"),
                new WorkSummaryDraftRequest(" ","작가"),
                new WorkSummaryDraftRequest("작품",null),
                new WorkSummaryDraftRequest("작품"," "),
                new WorkSummaryDraftRequest("가".repeat(101),"작가"))) {
            var error = assertThrows(BusinessException.class, () -> service.generate(request));
            assertEquals(400,error.getErrorCode().getStatus().value());
            assertEquals("WORK_DRAFT_INPUT_INVALID",error.getErrorCode().getCode());
        }
        assertThrows(BusinessException.class, () -> service.generate(null));
        verifyNoInteractions(client);
    }

    @Test void rejectsEmptyLongOrWrongTypeAiOutput() {
        for (String json : List.of("{}", "{\"summary\":null}", "{\"summary\":3}",
                "{\"summary\":\"   \"}", "{\"summary\":\""+"가".repeat(101)+"\"}")) {
            when(client.createJson(anyString(),anyString(),anyInt(),anyString(),anyMap()))
                    .thenReturn(new AiResult<>(mapper.readTree(json),null));
            var error=assertThrows(BusinessException.class, () -> service.generate(request()));
            assertEquals(502,error.getErrorCode().getStatus().value());
            assertEquals("AI_UPSTREAM_ERROR",error.getErrorCode().getCode());
        }
    }

    @Test void acceptsExactly100Characters() {
        when(client.createJson(anyString(),anyString(),anyInt(),anyString(),anyMap()))
                .thenReturn(new AiResult<>(mapper.readTree("{\"summary\":\""+"가".repeat(100)+"\"}"),null));
        assertEquals(100,service.generate(request()).summary().length());
    }

    @Test void preservesSpecifiedAiErrors() {
        for (var code : List.of(ErrorCode.AI_UPSTREAM_ERROR,
                ErrorCode.AI_CONFIGURATION_MISSING,ErrorCode.AI_TIMEOUT)) {
            doThrow(new BusinessException(code)).when(client)
                    .createJson(anyString(),anyString(),anyInt(),anyString(),anyMap());
            var error=assertThrows(BusinessException.class, () -> service.generate(request()));
            assertEquals(code.getStatus().value(),error.getErrorCode().getStatus().value());
            assertEquals(code.getCode(),error.getErrorCode().getCode());
            assertEquals(code.getMessage(),error.getMessage());
        }
    }

    @Test void malformedJsonFromClientBecomes502() {
        when(client.createJson(anyString(),anyString(),anyInt(),anyString(),anyMap()))
                .thenThrow(new IllegalArgumentException("invalid AI JSON"));
        assertEquals(502,assertThrows(BusinessException.class,
                () -> service.generate(request())).getErrorCode().getStatus().value());
    }

    @Test void missingRealClientKeyReturns503WithoutNetworkCall() {
        var noKeyClient = new OpenAiClient(mapper,"","https://api.openai.com/v1","unused","");
        var noKeyService = new WorkSummaryDraftService(noKeyClient,mapper);
        assertEquals(503,assertThrows(BusinessException.class,
                () -> noKeyService.generate(request())).getErrorCode().getStatus().value());
    }

    @Test
    void controllerReturnsSummary() {
        var mocked = mock(WorkSummaryDraftService.class);
        var controller = new WorkSummaryDraftController(mocked);
        when(mocked.generate(any())).thenReturn(new WorkSummaryDraftResponse("작품 소개"));
        assertEquals("작품 소개", controller.generate(request()).summary());
    }
}
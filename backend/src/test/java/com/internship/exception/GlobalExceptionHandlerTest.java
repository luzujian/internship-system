package com.internship.exception;

import com.internship.entity.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class EchoController {
        @PostMapping("/echo")
        public Result echo(@RequestBody Map<String, String> body) {
            return Result.success(body);
        }

        @GetMapping("/denied")
        public Result denied() {
            throw new org.springframework.security.access.AccessDeniedException("internal object id");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void directHandlerReturns400WithFriendlyMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Result> response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("JSON parse error", new RuntimeException("cause")));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertEquals("请求参数格式错误，请检查后重试", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("HttpMessageNotReadable"));
        assertFalse(response.getBody().getMessage().contains("cause"));
    }

    @Test
    void malformedJsonBodyMappedTo400WithoutStack() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数格式错误，请检查后重试"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("HttpMessageNotReadable"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at com.internship"))));
    }

    @Test
    void validJsonBodyStillPasses() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deniedBusinessObjectReturns403WithoutLeakingDetails() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("您没有访问权限"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal object id"))));
    }
    @org.junit.jupiter.api.Test
    void agentConfirmationConflictKeepsMachineCodeAndHttp409() {
        var result = new GlobalExceptionHandler().handleAgentException(new com.internship.agent.business.AgentException("CONFIG_CHANGED", "请重新预览", 409));
        org.junit.jupiter.api.Assertions.assertEquals(409,result.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals("CONFIG_CHANGED",result.getBody().get("errorCode"));
    }
}

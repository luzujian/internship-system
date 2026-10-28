package com.internship.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.Result;
import com.internship.service.ReflectionFeatureService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/** A single gate covers old and current URLs, including reads and downloads. */
@Component
@RequiredArgsConstructor
public class ReflectionFeatureInterceptor implements HandlerInterceptor {
    private static final Set<String> CONTROLLERS = Set.of(
            "StudentInternshipReflectionController", "InternshipReflectionController",
            "StudentReflectionAIAnalysisController", "StudentReflectionEvaluationController",
            "CounselorAIAnalysisController", "CounselorEvaluationController",
            "EvaluationManagementController", "InternshipAIAnalysisController", "InternshipEvaluationController",
            "CounselorAISettingsController", "CounselorScoringRuleController", "CounselorKeywordController",
            "ScoringRuleController", "KeywordLibraryController", "CategoryWeightController", "AIAnalysisSettingsController");
    private final ReflectionFeatureService feature;
    private final ObjectMapper json;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;
        String controller = method.getBeanType().getSimpleName();
        if ("BusinessFeatureController".equals(controller)) return true;
        String path = java.util.Objects.toString(request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE), request.getRequestURI());
        boolean reflectionEndpoint = ("SettingsController".equals(controller) && path.endsWith("/ai-analysis"))
                || CONTROLLERS.contains(controller)
                || path.matches(".*/(?:internship-reflection|reflections?|reflection-evaluation)(?:/.*)?");
        if (!reflectionEndpoint || feature.enabled()) return true;
        response.setStatus(HttpServletResponse.SC_GONE);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), new Result(410, ReflectionFeatureService.DISABLED_MESSAGE,
                java.util.Map.of("feature", "internshipReflection", "enabled", false)));
        return false;
    }
}

package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 LLM 路由服务集成测试。
 *
 * <p>需要 LLM API key 才能跑（调真实 routingChatModel）。CI 环境（无 secrets）自动跳过，
 * 避免红灯——本地有 {@code LLM_ROUTING_API_KEY} OS 环境变量时才跑。
 *
 * <p>注意：{@code spring-dotenv} 加载的 .env 不会注入 OS 环境变量，所以本地跑需要
 * {@code export LLM_ROUTING_API_KEY=...} 或在 IDE Run Configuration 里设。
 * 不设时本测试跳过（不算失败）。
 */
@Slf4j
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LLM_ROUTING_API_KEY", matches = ".+",
        disabledReason = "需要 OS env LLM_ROUTING_API_KEY（CI 无 secrets 时跳过；本地需 export 或 IDE 配置）")
public class AiCodeGenTypeRoutingServiceTest {

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    @Test
    public void testRouteCodeGenType() {
        String userPrompt = "做一个简单的个人介绍页面";
        CodeGenTypeEnum result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, result.getValue());
        userPrompt = "做一个公司官网，需要首页、关于我们、联系我们三个页面";
        result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, result.getValue());
        userPrompt = "做一个电商管理系统，包含用户管理、商品管理、订单管理，需要路由和状态管理";
        result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, result.getValue());
    }
}
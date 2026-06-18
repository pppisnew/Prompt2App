package com.prompt2app.agent.workflow.node;

import com.prompt2app.agent.workflow.state.WorkflowContext;
import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.infra.utils.SpringContextUtil;
import com.prompt2app.router.RoutingDecision;
import com.prompt2app.router.RoutingService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由工作节点（workflow demo 路径）。
 *
 * <p>使用 Phase 4 引入的两层 {@link RoutingService}（规则 + LLM 兜底）；
 * RoutingService 自身已有 LLM 错误兜底，本节点仅在编排层加 try-catch 防御 RuntimeException。
 */
@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType;
            try {
                RoutingService routingService = SpringContextUtil.getBean(RoutingService.class);
                RoutingDecision decision = routingService.route(context.getOriginalPrompt());
                generationType = decision.getStrategy();
                log.info("AI 智能路由完成，类型: {} ({}) layer={} {}ms",
                        generationType.getValue(), generationType.getText(),
                        decision.getLayer(), decision.getDurationMs());
            } catch (Exception e) {
                log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                generationType = CodeGenTypeEnum.HTML;
            }

            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }
}
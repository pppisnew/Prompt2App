package com.prompt2app.infra.constant;

/**
 * 应用领域常量。
 *
 * <p>仅保留与业务规则相关的不可变常量。所有<strong>部署变量</strong>（路径、host、端口、阈值、密钥等）
 * 已迁移至 {@link com.prompt2app.infra.config.Prompt2AppProperties}（ADR-0010）。
 *
 * <p>判断准则：
 * <ul>
 *   <li>修改后需要重启 / 改代码 / 改测试 ⇒ 业务常量（保留在此）</li>
 *   <li>修改后只影响运行时部署（不同环境取不同值）⇒ 配置项（迁出）</li>
 * </ul>
 */
public interface AppConstant {

    /**
     * 精选应用的优先级（业务规则：定义"精选"的语义阈值）。
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级（业务规则：新建应用的初始优先级）。
     */
    Integer DEFAULT_APP_PRIORITY = 0;
}

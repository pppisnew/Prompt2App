package com.prompt2app.agent.codegen.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 构建 Vue 项目
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在：{}", projectPath);
            return false;
        }
        // 检查是否有 package.json 文件
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            log.error("项目目录中没有 package.json 文件：{}", projectPath);
            return false;
        }
        log.info("开始构建 Vue 项目：{}", projectPath);
        // 执行 npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败：{}", projectPath);
            return false;
        }
        // 执行 npm run build
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败：{}", projectPath);
            return false;
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("构建完成但 dist 目录未生成：{}", projectPath);
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
        return true;
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }

    /**
     * 根据操作系统构造命令
     *
     * @param baseCommand
     * @return
     */
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 操作系统检测
     *
     * @return
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令
     *
     * <p>用 {@link ProcessBuilder} 替代 {@code RuntimeUtil.exec}，关键改进（根因 B）：
     * <ul>
     *   <li>{@code redirectErrorStream(true)} 合并 stderr→stdout，避免进程 buffer 满死锁；</li>
     *   <li>独立线程读取输出，失败时 log stdout/stderr tail（不再是裸 exitCode）；</li>
     *   <li>命令找不到时（exitCode=127 或 IOException）追加 PATH 诊断，提示 npm 可能不在 PATH。</li>
     * </ul>
     *
     * @param workingDir     工作目录
     * @param command        命令字符串（空格分割，不处理引号——已知遗留，非本 task scope）
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
        // redirectErrorStream：合并 stderr 到 stdout，单流读取防 buffer 满死锁
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"))
                .directory(workingDir)
                .redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (java.io.IOException e) {
            // 典型：npm 不在 PATH → "Cannot run program \"npm\""
            log.error("命令启动失败: {} | 错误: {} | 当前 PATH={} | 提示：若 npm 通过 nvm 安装，"
                            + "请确认启动 SpringBoot 的环境 PATH 含 node/npm 所在目录",
                    command, e.getMessage(), System.getenv("PATH"));
            return false;
        }

        // 独立线程读取合并输出，避免输出 buffer 满导致 waitFor 死锁
        StringBuilder outputBuf = new StringBuilder();
        Thread reader = Thread.ofVirtual().name("vue-build-stdout").start(() -> {
            try (InputStream is = process.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    outputBuf.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // 读流出错不影响主流程判定，跳过
            }
        });

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程: {}", timeoutSeconds, command);
                process.destroyForcibly();
                reader.interrupt();
                return false;
            }
            reader.join(2000); // 等输出读完
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            }
            // 失败：log exitCode + 输出 tail（最多 30 行），便于定位 npm/rollup 真实错误
            String tail = tailLines(outputBuf.toString(), 30);
            if (exitCode == 127) {
                log.error("命令执行失败，退出码 {}（命令未找到）: {} | 输出 tail:\n{} | 当前 PATH={}",
                        exitCode, command, tail, System.getenv("PATH"));
            } else {
                log.error("命令执行失败，退出码 {}: {} | 输出 tail:\n{}", exitCode, command, tail);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("命令执行被中断: {}", command);
            process.destroyForcibly();
            return false;
        }
    }

    /** 取输出末尾最多 n 行，超出以省略号提示。 */
    private String tailLines(String output, int maxLines) {
        if (output == null || output.isEmpty()) return "(no output)";
        String[] lines = output.split("\n");
        if (lines.length <= maxLines) return output.trim();
        int from = lines.length - maxLines;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "(... %d lines omitted ...)\n", from));
        for (int i = from; i < lines.length; i++) sb.append(lines[i]).append('\n');
        return sb.toString().trim();
    }

}

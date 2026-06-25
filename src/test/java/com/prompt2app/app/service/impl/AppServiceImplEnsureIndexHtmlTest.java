package com.prompt2app.app.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AppServiceImpl#ensureIndexHtml(File)} 兜底逻辑单元测试。
 *
 * <p>用反射调 private 方法（不依赖 Spring 上下文 / DB / LLM）。
 * 覆盖 4 场景：已有 index / 有"首页"标题 / 无"首页"标题取第一个 / 空目录。
 */
class AppServiceImplEnsureIndexHtmlTest {

    @TempDir
    Path tmpDir;

    /** 反射调 private ensureIndexHtml */
    private void invokeEnsureIndexHtml(File deployDir) throws Exception {
        Method m = AppServiceImpl.class.getDeclaredMethod("ensureIndexHtml", File.class);
        m.setAccessible(true);
        // AppServiceImpl extends ServiceImpl，有无参构造（字段全部 null 但 ensureIndexHtml 不依赖它们）
        AppServiceImpl service = new AppServiceImpl();
        m.invoke(service, deployDir);
    }

    private void writeFile(File dir, String name, String content) throws Exception {
        Files.writeString(dir.toPath().resolve(name), content);
    }

    @Test
    void alreadyHasIndexHtml_notModified() throws Exception {
        File dir = tmpDir.toFile();
        writeFile(dir, "index.html", "<!DOCTYPE html><html><body>原 index</body></html>");
        writeFile(dir, "其他页.html", "<!DOCTYPE html><html><body>其他</body></html>");

        invokeEnsureIndexHtml(dir);

        // index.html 内容不变（没被覆盖）
        String content = Files.readString(dir.toPath().resolve("index.html"));
        assertEquals("<!DOCTYPE html><html><body>原 index</body></html>", content);
    }

    @Test
    void multiFileWithShouyeTitle_copiesAsIndex() throws Exception {
        File dir = tmpDir.toFile();
        writeFile(dir, "米哈游角色图鉴-首页.html", "<!DOCTYPE html><html><body>首页内容</body></html>");
        writeFile(dir, "米哈游角色图鉴-关于.html", "<!DOCTYPE html><html><body>关于内容</body></html>");

        invokeEnsureIndexHtml(dir);

        File index = new File(dir, "index.html");
        assertTrue(index.exists(), "应兜底生成 index.html");
        String content = Files.readString(index.toPath());
        assertEquals("<!DOCTYPE html><html><body>首页内容</body></html>", content);
    }

    @Test
    void multiFileNoShouyeTitle_copiesFirstAlphabetical() throws Exception {
        File dir = tmpDir.toFile();
        // 无"首页"/"index"/"home"的文件名
        writeFile(dir, "关于.html", "<!DOCTYPE html><html><body>关于</body></html>");
        writeFile(dir, "角色详情.html", "<!DOCTYPE html><html><body>详情</body></html>");

        invokeEnsureIndexHtml(dir);

        File index = new File(dir, "index.html");
        assertTrue(index.exists(), "应兜底生成 index.html");
        // 字母序第一个：'关' < '角'，所以"关于.html"被选中
        String content = Files.readString(index.toPath());
        assertEquals("<!DOCTYPE html><html><body>关于</body></html>", content);
    }

    @Test
    void emptyDeployDir_doesNotThrow() throws Exception {
        File dir = tmpDir.toFile();
        // 空目录，无任何文件

        assertDoesNotThrow(() -> invokeEnsureIndexHtml(dir));

        // 不应生成 index.html（无 HTML 可复制）
        assertFalse(new File(dir, "index.html").exists());
    }
}

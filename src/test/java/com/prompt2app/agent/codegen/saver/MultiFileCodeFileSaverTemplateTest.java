package com.prompt2app.agent.codegen.saver;

import com.prompt2app.agent.model.MultiFileCodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MultiFileCodeFileSaverTemplate} 单元测试。
 *
 * <p>核心覆盖（方案 A，2026-06-26 task）：孤儿文件名注释（无 DOCTYPE）被 filter 丢弃，
 * 不再生成空壳 HTML 文件。
 */
class MultiFileCodeFileSaverTemplateTest {

    @TempDir
    Path tmpDir;

    private MultiFileCodeFileSaverTemplate saver() {
        return new MultiFileCodeFileSaverTemplate(tmpDir.toString());
    }

    private File[] htmlFiles() {
        // saveCode 用 appId 拼 multi_file_<appId> 子目录，html 文件在子目录里
        File[] subDirs = tmpDir.toFile().listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) return null;
        return subDirs[0].listFiles((d, n) -> n.endsWith(".html"));
    }

    @Test
    void orphanCommentsFiltered_onlyRealPagesSaved() {
        // 模拟本次 bug 的 LLM 输出：4 个孤儿注释 + 4 个 DOCTYPE 页
        String htmlCode = """
                <!-- index.html -->
                <!-- about.html -->
                <!-- bestiary.html -->
                <!-- creature.html -->
                <!DOCTYPE html>
                <html><head><title>关于</title></head><body>关于页内容</body></html>
                <!DOCTYPE html>
                <html><head><title>图鉴</title></head><body>图鉴页内容</body></html>
                <!DOCTYPE html>
                <html><head><title>详情</title></head><body>详情页内容</body></html>
                <!DOCTYPE html>
                <html><head><title>首页</title></head><body>首页内容</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        saver().saveCode(result, 100001L);

        File[] htmls = htmlFiles();
        assertNotNull(htmls);
        // 4 个真实页，不应有 4 个空壳（共 8 个）
        assertEquals(4, htmls.length, "应只保存 4 个真实页，不含空壳");

        // 确认没有 19-22 字节的空壳
        for (File f : htmls) {
            long size = f.length();
            assertTrue(size > 50, f.getName() + " 太小（" + size + "B），可能是空壳");
        }
    }

    @Test
    void singlePage_savedAsOneFile() {
        String htmlCode = """
                <!DOCTYPE html>
                <html><head><title>单页</title></head><body>内容</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        saver().saveCode(result, 100002L);

        File[] htmls = htmlFiles();
        assertNotNull(htmls);
        assertEquals(1, htmls.length);
    }

    @Test
    void multiPageWithFileSeparator_realPagesOnly() {
        // FILE_SEPARATOR 路径：parser 注入的，含一个孤儿注释
        String htmlCode = """
                <!-- orphan.html -->
                <!-- ===== FILE_SEPARATOR ===== -->
                <!DOCTYPE html>
                <html><head><title>页一</title></head><body>内容一</body></html>
                <!-- ===== FILE_SEPARATOR ===== -->
                <!DOCTYPE html>
                <html><head><title>页二</title></head><body>内容二</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        saver().saveCode(result, 100003L);

        File[] htmls = htmlFiles();
        assertNotNull(htmls);
        assertEquals(2, htmls.length, "孤儿注释应被 filter，只存 2 个真实页");
    }

    @Test
    void onlyOrphanComments_throwsOrSavesNothing() {
        // 只有孤儿注释，无任何 DOCTYPE
        String htmlCode = """
                <!-- index.html -->
                <!-- about.html -->
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        // validateInput 应抛异常（htmlCode 非空但 splitHtmlPages 返回空 → saveFiles 写 0 个 html）
        // 实际行为：saveFiles 不抛异常但写 0 个 html 文件（css/js 仍写）
        // 这里验证：不应有空壳 html 产生
        MultiFileCodeResult withCss = new MultiFileCodeResult();
        withCss.setHtmlCode(htmlCode);
        withCss.setCssCode("body{}");
        withCss.setJsCode("console.log(1)");

        saver().saveCode(withCss, 100004L);

        File[] htmls = htmlFiles();
        if (htmls != null) {
            assertEquals(0, htmls.length, "不应有任何空壳 html 文件");
        }
    }
}

package com.prompt2app.agent.codegen.parser;

import com.prompt2app.agent.model.MultiFileCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MultiFileCodeParser} 单元测试。
 *
 * <p>核心覆盖（方案 A，2026-06-26 task）：孤儿文件名注释（无 DOCTYPE）被 filter 丢弃。
 */
class MultiFileCodeParserTest {

    private final MultiFileCodeParser parser = new MultiFileCodeParser();

    @Test
    void orphanCommentsFiltered_onlyRealPagesInResult() {
        // 模拟 bug 的 LLM 输出：4 孤儿注释 + 4 DOCTYPE 页（在一个 ```html 块里）
        String llmOutput = """
                ```html
                <!-- index.html -->
                <!-- about.html -->
                <!-- bestiary.html -->
                <!-- creature.html -->
                <!DOCTYPE html>
                <html><head><title>关于</title></head><body>关于</body></html>
                <!DOCTYPE html>
                <html><head><title>图鉴</title></head><body>图鉴</body></html>
                <!DOCTYPE html>
                <html><head><title>详情</title></head><body>详情</body></html>
                <!DOCTYPE html>
                <html><head><title>首页</title></head><body>首页</body></html>
                ```
                """;

        MultiFileCodeResult result = parser.parseCode(llmOutput);

        assertNotNull(result.getHtmlCode());
        // htmlCode 应只含 4 个真实页，不含 4 个孤儿注释
        // FILE_SEPARATOR 数 = 页数 - 1 = 3
        long separatorCount = countOccurrences(result.getHtmlCode(),
                "<!-- ===== FILE_SEPARATOR ===== -->");
        assertEquals(3, separatorCount, "应只有 4 个真实页（3 个分隔符），孤儿注释被 filter");

        // htmlCode 不应含孤儿注释（被 filter 丢弃）
        assertFalse(result.getHtmlCode().trim().startsWith("<!-- index.html -->"),
                "htmlCode 不应以孤儿注释开头");
    }

    @Test
    void singlePageParsedCorrectly() {
        String llmOutput = """
                ```html
                <!DOCTYPE html>
                <html><head><title>单页</title></head><body>内容</body></html>
                ```
                """;

        MultiFileCodeResult result = parser.parseCode(llmOutput);

        assertNotNull(result.getHtmlCode());
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE"));
        assertFalse(result.getHtmlCode().contains("FILE_SEPARATOR"));
    }

    @Test
    void cssAndJsExtractedSeparately() {
        String llmOutput = """
                ```html
                <!DOCTYPE html>
                <html><head><title>页</title></head><body>内容</body></html>
                ```
                ```css
                body { color: red; }
                ```
                ```js
                console.log("hello");
                ```
                """;

        MultiFileCodeResult result = parser.parseCode(llmOutput);

        assertNotNull(result.getHtmlCode());
        assertEquals("body { color: red; }", result.getCssCode());
        assertEquals("console.log(\"hello\");", result.getJsCode());
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}

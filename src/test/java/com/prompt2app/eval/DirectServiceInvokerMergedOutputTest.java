package com.prompt2app.eval;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DirectServiceInvoker#readMergedOutput(File, CodeGenTypeEnum)} VUE 分支方案 D 验证。
 *
 * <p>覆盖：
 * <ul>
 *   <li>VUE + 有 dist：mergedOutput 含 package.json 字面量 + 源码符号，不含 dist 内容</li>
 *   <li>VUE + 无 dist：mergedOutput 含源码（与旧行为一致）</li>
 *   <li>VUE + 有 node_modules：mergedOutput 不含 node_modules 内容</li>
 *   <li>HTML：mergedOutput 行为不变（回归保护）</li>
 * </ul>
 *
 * <p>用反射调用 private 方法 readMergedOutput，避免改其可见性。
 */
class DirectServiceInvokerMergedOutputTest {

    /** 用反射调用 private readMergedOutput(File, CodeGenTypeEnum)。 */
    private String invokeReadMergedOutput(File dir, CodeGenTypeEnum genType) throws Exception {
        var m = DirectServiceInvoker.class.getDeclaredMethod("readMergedOutput", File.class, CodeGenTypeEnum.class);
        m.setAccessible(true);
        // DirectServiceInvoker 构造需要 facade + properties，但 readMergedOutput 不依赖它们
        // 用反射创建实例跳过构造（Unsafe 或 sun.misc 不可移植，改用 null + 不调用任何依赖字段的方法）
        var ctor = DirectServiceInvoker.class.getDeclaredConstructor(
                com.prompt2app.agent.codegen.AiCodeGeneratorFacade.class,
                com.prompt2app.infra.config.Prompt2AppProperties.class);
        ctor.setAccessible(true);
        DirectServiceInvoker invoker = ctor.newInstance(null, null);
        return (String) m.invoke(invoker, dir, genType);
    }

    private void writeFile(Path dir, String relPath, String content) throws Exception {
        Path p = dir.resolve(relPath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void vue_withDist_readsSourceAndFileTree_notDist(@TempDir Path tmp) throws Exception {
        Path project = tmp.resolve("vue_project_test");
        writeFile(project, "package.json", "{\"name\":\"app\",\"dependencies\":{\"vue\":\"^3.4.0\"}}");
        writeFile(project, "src/main.js", "import { ref } from 'vue'\nconst count = ref(0)\nlocalStorage.setItem('k','v')\naddEventListener('click', () => {})");
        writeFile(project, "src/App.vue", "<template><div>{{ count }}</div></template>");
        // dist 里是 minified JS，不该出现在 mergedOutput
        writeFile(project, "dist/index.html", "<!DOCTYPE html><script src=/assets/index-abc.js>");
        writeFile(project, "dist/assets/index-abc.js", "var r=0;console.log(r);");

        String merged = invokeReadMergedOutput(project.toFile(), CodeGenTypeEnum.VUE_PROJECT);

        // 文件清单让 "package.json" 字面量可命中
        assertTrue(merged.contains("=== file: package.json ==="), "file tree should list package.json");
        // 源码符号可命中
        assertTrue(merged.contains("ref"), "source should contain 'ref'");
        assertTrue(merged.contains("localStorage"), "source should contain 'localStorage'");
        assertTrue(merged.contains("addEventListener"), "source should contain 'addEventListener'");
        assertTrue(merged.contains("vue"), "package.json deps should contain 'vue'");
        // dist 内容不该出现（minified JS 中的 console.log 是 dist 专属标记）
        assertFalse(merged.contains("console.log"), "dist minified JS should NOT be in mergedOutput");
        assertFalse(merged.contains("index-abc.js"), "dist asset filename should NOT be in mergedOutput");
    }

    @Test
    void vue_withoutDist_readsSource(@TempDir Path tmp) throws Exception {
        Path project = tmp.resolve("vue_no_dist");
        writeFile(project, "package.json", "{\"name\":\"app\"}");
        writeFile(project, "src/App.vue", "<template><div>hi</div></template>");

        String merged = invokeReadMergedOutput(project.toFile(), CodeGenTypeEnum.VUE_PROJECT);

        assertTrue(merged.contains("=== file: package.json ==="));
        assertTrue(merged.contains("<template>"));
        // 无 dist 时也不该有 "=== source contents ===" 之前的内容缺失
        assertTrue(merged.contains("=== source contents ==="));
    }

    @Test
    void vue_skipsNodeModules(@TempDir Path tmp) throws Exception {
        Path project = tmp.resolve("vue_with_nm");
        writeFile(project, "package.json", "{\"name\":\"app\"}");
        writeFile(project, "src/App.vue", "<template>real</template>");
        // node_modules 里有大量无关内容，不该被读
        writeFile(project, "node_modules/vue/index.js", "module.exports = function fakeVue() {}");
        writeFile(project, "node_modules/vue/dist/vue.js", "var fakeMinified = 'should_not_appear'");

        String merged = invokeReadMergedOutput(project.toFile(), CodeGenTypeEnum.VUE_PROJECT);

        assertTrue(merged.contains("=== file: package.json ==="));
        assertTrue(merged.contains("real"), "src/App.vue content should appear");
        assertFalse(merged.contains("fakeVue"), "node_modules content should NOT appear");
        assertFalse(merged.contains("fakeMinified"), "node_modules dist content should NOT appear");
        assertFalse(merged.contains("=== file: node_modules"), "file tree should not list node_modules files");
    }

    @Test
    void html_readsAllFiles_unchanged(@TempDir Path tmp) throws Exception {
        Path project = tmp.resolve("html_test");
        writeFile(project, "index.html", "<html><body><h1>Hello World this is long enough body text</h1></body></html>");
        writeFile(project, "style.css", "body { color: red; }");

        String merged = invokeReadMergedOutput(project.toFile(), CodeGenTypeEnum.HTML);

        // HTML 策略不变：读所有文件内容，无文件清单前缀
        assertTrue(merged.contains("<html>"));
        assertTrue(merged.contains("color: red"));
        // HTML 不该有 VUE 的文件清单格式
        assertFalse(merged.contains("=== file:"));
        assertFalse(merged.contains("=== source contents ==="));
    }
}
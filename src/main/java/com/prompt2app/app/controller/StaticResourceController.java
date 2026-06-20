package com.prompt2app.app.controller;

import com.prompt2app.infra.config.Prompt2AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

/**
 * 静态资源访问。
 *
 * <p>预览根目录由 {@link Prompt2AppProperties#getStorage()}.codeDeployDir 提供（ADR-0010）。
 * 部署后的应用文件在 {@code <codeDeployDir>/<deployKey>/} 下（见 AppServiceImpl.deployApp）。
 *
 * <p>访问格式：{@code http://localhost:8123/api/static/{deployKey}[/{fileName}]}
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    @jakarta.annotation.Resource
    private Prompt2AppProperties properties;

    /**
     * 提供静态资源访问，支持目录重定向。
     *
     * <p>路径解析用 {@link HttpServletRequest#getRequestURI()} + context-path 去除，
     * 不依赖 {@code PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}（在 Spring Boot 3.x +
     * context-path 下的行为有版本差异）。
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 1. 解析 context-path 之后、/static/{deployKey} 之后的资源子路径
            //    例：getRequestURI() = /api/static/7b9Hln/index.html
            //        contextPath    = /api
            //        去掉 contextPath -> /static/7b9Hln/index.html
            //        去掉 /static/{deployKey} -> /index.html
            String requestUri = request.getRequestURI();
            String contextPath = request.getContextPath();
            String pathAfterContext = requestUri.startsWith(contextPath)
                    ? requestUri.substring(contextPath.length())
                    : requestUri;
            String prefix = "/static/" + deployKey;
            String resourcePath = pathAfterContext.startsWith(prefix)
                    ? pathAfterContext.substring(prefix.length())
                    : "";

            // 2. 空路径或 / 结尾 → 视为目录访问，默认返回 index.html
            if (resourcePath.isEmpty()) {
                // 不带斜杠访问 /static/{key} → 301 重定向到 /static/{key}/
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", requestUri + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }

            // 3. 构建文件路径：优先从 codeDeployDir（部署后的应用），fallback 到 codeOutputDir（生成后未部署的预览）
            //    两条路径共用此 controller：
            //    - 部署后访问：key=random6（如 7b9Hln），文件在 codeDeployDir/{key}/
            //    - 生成后预览：key=html_{appId}（如 html_425860387930632192），文件在 codeOutputDir/{key}/
            File file = resolveFile(deployKey, resourcePath);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            // 4. 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(file.getAbsolutePath()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 按 deployDir → outputDir 顺序查找文件，返回第一个存在的 File，都不存在返回 null。
     */
    private File resolveFile(String deployKey, String resourcePath) {
        String deployPath = properties.getStorage().getCodeDeployDir() + "/" + deployKey + resourcePath;
        File deployFile = new File(deployPath);
        if (deployFile.exists() && deployFile.isFile()) {
            return deployFile;
        }
        String outputPath = properties.getStorage().getCodeOutputDir() + "/" + deployKey + resourcePath;
        File outputFile = new File(outputPath);
        if (outputFile.exists() && outputFile.isFile()) {
            return outputFile;
        }
        return null;
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type。
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}

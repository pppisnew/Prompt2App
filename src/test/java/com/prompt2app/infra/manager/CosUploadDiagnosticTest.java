package com.prompt2app.infra.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.Bucket;
import com.qcloud.cos.model.PutObjectResult;
import com.prompt2app.infra.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * COS 上传集成诊断测试。
 *
 * <p>不是常规回归测试——是事故排查工具。逐步隔离 COS 上传失败的原因：
 * <ol>
 *   <li>配置 binding 是否正确（bucket/region/secretId/secretKey）</li>
 *   <li>COSClient 能否连通腾讯云（listBuckets 验证凭证 + 网络）</li>
 *   <li>目标 bucket 是否存在于当前 APPID 下</li>
 *   <li>能否实际 putObject 上传一个小文件</li>
 * </ol>
 *
 * <p>用法：{@code mvn test -Dtest=CosUploadDiagnosticTest -Dspring.profiles.active=dev}
 * <p>需要 .env 里有真实的 COS_* 配置 + 网络能访问腾讯云。
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CosUploadDiagnosticTest {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    @Test
    @Order(1)
    @DisplayName("Step 1 · 配置 binding 检查")
    void step1_configBinding() {
        log.info("========== Step 1 · 配置 binding ==========");
        log.info("bucket    = [{}]", cosClientConfig.getBucket());
        log.info("region    = [{}]", cosClientConfig.getRegion());
        log.info("host      = [{}]", cosClientConfig.getHost());
        log.info("secretId  = [{}]",
                cosClientConfig.getSecretId() != null
                        ? cosClientConfig.getSecretId().substring(0, Math.min(12, cosClientConfig.getSecretId().length())) + "***"
                        : "(null)");
        log.info("secretKey = [{}]",
                cosClientConfig.getSecretKey() != null
                        ? "(" + cosClientConfig.getSecretKey().length() + " chars)"
                        : "(null)");

        // 断言关键字段非空
        org.junit.jupiter.api.Assertions.assertNotNull(cosClientConfig.getBucket(), "bucket 为 null — .env COS_BUCKET 未加载");
        org.junit.jupiter.api.Assertions.assertNotNull(cosClientConfig.getRegion(), "region 为 null — .env COS_REGION 未加载");
        org.junit.jupiter.api.Assertions.assertNotNull(cosClientConfig.getSecretId(), "secretId 为 null — .env COS_SECRET_ID 未加载");
        org.junit.jupiter.api.Assertions.assertNotNull(cosClientConfig.getSecretKey(), "secretKey 为 null — .env COS_SECRET_KEY 未加载");
        log.info("Step 1 ✅ 配置 binding 正常");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2 · COSClient 连通性 + 凭证验证（listBuckets）")
    void step2_connectivityAndCredentials() {
        log.info("========== Step 2 · listBuckets 验证凭证 + 网络 ==========");
        try {
            List<Bucket> buckets = cosClient.listBuckets();
            log.info("listBuckets 成功，返回 {} 个 bucket：", buckets.size());
            for (Bucket b : buckets) {
                log.info("  - {} (region={})", b.getName(), b.getLocation());
            }
            log.info("Step 2 ✅ 凭证有效 + 网络连通");
        } catch (CosServiceException e) {
            log.error("Step 2 ❌ CosServiceException: errorCode={}, errorMessage={}, requestId={}",
                    e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw e;
        } catch (CosClientException e) {
            log.error("Step 2 ❌ CosClientException（网络/客户端）: {}", e.getMessage());
            throw e;
        }
    }

    @Test
    @Order(3)
    @DisplayName("Step 3 · 目标 bucket 是否存在于此 APPID 下")
    void step3_targetBucketExists() {
        log.info("========== Step 3 · 检查目标 bucket ==========");
        String targetBucket = cosClientConfig.getBucket();
        log.info("目标 bucket = [{}]", targetBucket);

        try {
            List<Bucket> buckets = cosClient.listBuckets();
            boolean found = buckets.stream()
                    .anyMatch(b -> b.getName().equals(targetBucket));
            if (found) {
                log.info("Step 3 ✅ bucket [{}] 存在于当前 APPID", targetBucket);
            } else {
                log.error("Step 3 ❌ bucket [{}] 不在当前 APPID 的 bucket 列表中！", targetBucket);
                log.error("当前 APPID 拥有的 bucket 列表见 Step 2 输出");
                log.error("可能原因：SecretId/Key 属于另一个腾讯云账号（APPID），或 bucket 名拼写错误");
                org.junit.jupiter.api.Assertions.fail(
                        "bucket " + targetBucket + " 不在当前凭证的 bucket 列表中");
            }
        } catch (Exception e) {
            log.error("Step 3 ❌ 查询 bucket 列表失败: {}", e.getMessage());
            throw e;
        }
    }

    @Test
    @Order(4)
    @DisplayName("Step 4 · 实际上传小文件")
    void step4_uploadSmallFile() {
        log.info("========== Step 4 · 上传测试文件 ==========");
        String bucket = cosClientConfig.getBucket();
        String testKey = "/cos-diagnostic-test/test_" + System.currentTimeMillis() + ".txt";

        File tempFile = null;
        try {
            // 创建临时测试文件
            tempFile = Files.createTempFile("cos-test", ".txt").toFile();
            java.nio.file.Files.writeString(tempFile.toPath(), "COS diagnostic test @ " + java.time.Instant.now());

            log.info("上传: bucket=[{}], key=[{}], file=[{}]", bucket, testKey, tempFile.getAbsolutePath());
            PutObjectResult result = cosClient.putObject(bucket, testKey, tempFile);
            log.info("Step 4 ✅ 上传成功！ETag={}", result.getETag());

            // 清理：删除测试对象
            cosClient.deleteObject(bucket, testKey);
            log.info("已清理测试对象: {}", testKey);

        } catch (CosServiceException e) {
            log.error("Step 4 ❌ CosServiceException: errorCode={}, errorMessage={}, requestId={}",
                    e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            log.error("常见 errorCode 含义：");
            log.error("  NoSuchBucket   → bucket 名/region 不匹配，或 bucket 不在此 APPID");
            log.error("  AccessDenied   → SecretId/Key 无此 bucket 的写权限");
            log.error("  InvalidBucketName → bucket 名格式不对（应为 name-appid）");
            throw new RuntimeException(e);
        } catch (java.io.IOException e) {
            log.error("Step 4 ❌ 创建临时文件失败: {}", e.getMessage());
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Step 4 ❌ 其他异常: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}

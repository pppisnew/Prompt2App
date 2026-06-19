package com.prompt2app.infra.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class WebScreenshotUtilsTest {

    @Test
    void saveWebPageScreenshot() {
        String testUrl = "https://www.codefather.cn";
        String screenshotsRoot = System.getProperty("user.dir") + "/tmp/screenshots";
        String webPageScreenshot = WebScreenshotUtils.saveWebPageScreenshot(testUrl, screenshotsRoot);
        Assertions.assertNotNull(webPageScreenshot);
    }
}

package com.sc1hub.common.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContext;

/**
 * 정적 리소스(css/js) 캐시 무효화용 버전 값.
 * 배포(재시작)마다 값이 바뀌어 JSP에서 ?v=${applicationScope.assetVersion}로 사용한다.
 */
@Component
public class AssetVersionInitializer implements ServletContextInitializer {

    @Override
    public void onStartup(ServletContext servletContext) {
        servletContext.setAttribute("assetVersion", String.valueOf(System.currentTimeMillis() / 1000));
    }
}

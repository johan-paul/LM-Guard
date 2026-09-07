package com.lmguard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Some Windows dev machines (observed with Docker Desktop/WSL2 and VirtualBox host-only
 * adapters installed) break the JDK's AF_UNIX loopback socket that Tomcat's default NIO
 * connector uses for its selector wakeup pipe, failing embedded Tomcat startup with
 * "Unable to establish loopback connection" (java.net.SocketException: Invalid argument).
 * NIO2 talks to Windows I/O completion ports directly instead of a Selector/Pipe, sidestepping
 * the bug entirely. Scoped to Windows only so Linux deployments keep Tomcat's default NIO.
 */
@Configuration
@Slf4j
public class WindowsTomcatConnectorConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2OnWindows() {
        return factory -> {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                log.info("Windows detected: using Tomcat's NIO2 connector to avoid the JDK "
                        + "AF_UNIX loopback pipe bug some Windows dev machines hit with NIO");
                factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
            }
        };
    }
}

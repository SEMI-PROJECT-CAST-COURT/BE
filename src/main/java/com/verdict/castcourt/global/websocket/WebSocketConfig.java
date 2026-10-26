package com.verdict.castcourt.global.websocket;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/* WebSocket 주소 등록 : BaseWebSocketHandler 를 상속한 빈을 모두 찾아 각자의 path() 로 등록한다
 *  기능을 추가할 때 이 파일은 고치지 않는다.
 * */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    // CorsConfig 와 같은 React 주소 (HTTP CORS 설정은 WebSocket 핸드셰이크에 적용되지 않아 따로 둔다)
    //  - localhost:5173   : 내 PC 에서 접속
    //  - 192.168.*:5173   : 같은 와이파이의 다른 PC 에서 내 IP 로 접속 (npm run dev -- --host)
    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:5173",
            "http://192.168.*:5173"
    };

    // 핸들러가 하나도 없어도 서버가 뜨도록 ObjectProvider 로 받는다
    private final ObjectProvider<BaseWebSocketHandler> handlers;

    public WebSocketConfig(ObjectProvider<BaseWebSocketHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        handlers.orderedStream().forEach(handler ->
                registry.addHandler(handler, handler.path())
                        .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS));
    }
}

package org.example.web;

import com.impinj.octane.OctaneSdkException;
import org.example.reader.ReaderMain;
import org.example.reader.ReaderOperations;
import org.example.data.tagdata.MyTagData;
import org.example.data.tagdata.TagDataCache;
import org.example.util.text.JacksonJsonConverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@SpringBootApplication
@EnableWebSocket
public class WebSocketServer implements WebSocketConfigurer {

//    // 使用现有的TagDataCache组件
//    private final TagDataCache tagDataCache;
    private static final TagDataCache<MyTagData> dataCache = ReaderMain.dataCache;

    // 标签长时间未被读取到，则不再在前端显示
    private static final Duration outTimeDuration = Duration.ofSeconds(2);

    // WebSocket会话集合（使用线程安全的Set管理会话）
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    // 广播频率
    private static final long broadcastIntervalMs = 200;

    // 使用ScheduledExecutorService替代原生Thread
    private static final ScheduledExecutorService broadcastExecutor =
            Executors.newSingleThreadScheduledExecutor();

    // 数据读取线程池
    private static final ExecutorService dataReaderExecutor =
            Executors.newSingleThreadExecutor();

//    public WebSocketServer(TagDataCache tagDataCache) {
//        this.tagDataCache = tagDataCache;
//    }

    // 广播数据到前端
    @Bean
    public Runnable broadcastTask() {
        return () -> {
            try {
                Map<String, MyTagData> latestData =
                        dataCache.getInTimeData(outTimeDuration);
                String jsonData = JacksonJsonConverter.convertToJson(latestData);
//                System.out.println(jsonData);

                sessions.forEach(session -> {
                    try {
                        if (session.isOpen()) {
                            synchronized (session) {
                                session.sendMessage(new TextMessage(jsonData));
                            }
                        }
                    } catch (Exception e) {
                        handleSessionError(session, e);
                    }
                });
            } catch (Exception e) {
                System.err.println("Broadcast task failed: " + e.getMessage());
            }
        };
    }

    public static void main(String[] args) {
        // 关闭处理
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownExecutor(broadcastExecutor, "Broadcast executor");
            try {
                ReaderOperations.stopReader();
            } catch (OctaneSdkException e) {
                System.out.println(e.getMessage());
            }
            ReaderOperations.disconnectReader();
            shutdownExecutor(dataReaderExecutor, "Data reader executor");
        }));

        // 启动数据读取线程
        dataReaderExecutor.execute(ReaderMain::run);

        // 启动广播任务
        broadcastExecutor.scheduleAtFixedRate(
                SpringApplication.run(WebSocketServer.class, args)
                        .getBean(WebSocketServer.class).broadcastTask(),
                0, broadcastIntervalMs, TimeUnit.MILLISECONDS);
    }

    private static void handleSessionError(WebSocketSession session, Exception e) {
        System.err.printf("Failed to send message to session %s: %s%n",
                session.getId(), e.getMessage());
        sessions.remove(session);
        try {
            session.close();
        } catch (Exception ex) {
            System.err.println("Failed to close session: " + ex.getMessage());
        }
    }

    private static void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) return;
        try {
            // 第一阶段：平缓关闭
            executor.shutdown();  // 1. 停止接收新任务

            // 第二阶段：等待现有任务完成
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {  // 2. 等待5秒
                // 第三阶段：强制终止
                executor.shutdownNow();  // 3. 取消所有待处理任务
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();  // 4. 再次尝试强制终止
            Thread.currentThread().interrupt();  // 5. 恢复中断状态
            System.err.println(name + " shutdown interrupted");
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new DataHandler(), "/data").setAllowedOrigins("*");
    }

    static class DataHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            sessions.add(session);
            // 发送初始数据
            try {
                session.sendMessage(new TextMessage(JacksonJsonConverter.convertToJson(dataCache.getAllData())));
//                session.sendMessage(new TextMessage(JacksonJsonConverter.convertToJson(new HashMap<>())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            sessions.remove(session);
        }
    }
}

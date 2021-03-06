package com.github.dreamhead.moco.runner.watcher;

import com.github.dreamhead.moco.internal.MocoServer;
import com.google.common.base.Optional;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.io.CharSource.wrap;
import static io.netty.channel.ChannelHandler.Sharable;

public class ShutdownMocoRunnerWatcher implements MocoRunnerWatcher {
    private static Logger logger = LoggerFactory.getLogger(ShutdownMocoRunnerWatcher.class);
    private final MocoServer server = new MocoServer();
    private final Optional<Integer> shutdownPort;
    private final String shutdownKey;
    private final ShutdownListener shutdownListener;
    private int port;

    public ShutdownMocoRunnerWatcher(Optional<Integer> shutdownPort, String shutdownKey, ShutdownListener shutdownListener) {
        this.shutdownPort = shutdownPort;
        this.shutdownKey = shutdownKey;
        this.shutdownListener = shutdownListener;
    }

    public void startMonitor() {
        int port = server.start(this.shutdownPort.or(0), new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("handler", new ShutdownHandler());
            }
        });

        this.port = port;

        logger.info("Shutdown port is {}", port);
    }

    public void stopMonitor() {
        server.stop();
    }

    public int port() {
        return port;
    }

    @Sharable
    private class ShutdownHandler extends SimpleChannelInboundHandler<String> {
        private final ExecutorService service = Executors.newCachedThreadPool();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            if (shouldShutdown(msg)) {
                shutdownListener.onShutdown();
                shutdownMonitorSelf();
            }
        }

        private void shutdownMonitorSelf() {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    stopMonitor();
                }
            });
        }

        private boolean shouldShutdown(String message) {
            try {
                return shutdownKey.equals(wrap(message).readFirstLine());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

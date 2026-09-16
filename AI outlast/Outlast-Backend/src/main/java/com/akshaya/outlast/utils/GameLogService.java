package com.akshaya.outlast.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GameLogService {
    private final List<String> logs = new ArrayList<>();
    private PrintStream originalOut;
    private InterceptingStream interceptor;

    public synchronized void startCapturing() {
        if (originalOut != null) {
            return; // Already capturing
        }
        originalOut = System.out;
        interceptor = new InterceptingStream(originalOut);
        System.setOut(new PrintStream(interceptor, true));
    }

    public synchronized void stopCapturing() {
        if (originalOut != null) {
            System.setOut(originalOut);
            originalOut = null;
            interceptor = null;
        }
    }

    public synchronized List<String> getLogs(int fromIndex) {
        if (fromIndex < 0 || fromIndex >= logs.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(logs.subList(fromIndex, logs.size()));
    }

    public synchronized void addLogLine(String line) {
        logs.add(line);
    }

    public synchronized void clear() {
        logs.clear();
        if (interceptor != null) {
            interceptor.resetBuffer();
        }
    }

    private class InterceptingStream extends OutputStream {
        private final PrintStream target;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public InterceptingStream(PrintStream target) {
            this.target = target;
        }

        public void resetBuffer() {
            buffer.reset();
        }

        @Override
        public void write(int b) throws IOException {
            target.write(b); // Print to console
            
            if (b == '\n') {
                String line = buffer.toString("UTF-8").replace("\r", "");
                synchronized (GameLogService.this) {
                    logs.add(line);
                }
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            target.write(b, off, len);
            for (int i = 0; i < len; i++) {
                int ch = b[off + i];
                if (ch == '\n') {
                    String line = buffer.toString("UTF-8").replace("\r", "");
                    synchronized (GameLogService.this) {
                        logs.add(line);
                    }
                    buffer.reset();
                } else {
                    buffer.write(ch);
                }
            }
        }
    }
}

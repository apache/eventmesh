package org.apache.eventmesh.connector.mcp.source.data;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

/**
 * 流式结果类
 * 用于管理流式传输的数据块
 */
@Slf4j
public class MCPStreamingResponse implements Serializable {
    private final String[] chunks;
    @Getter
    private int currentIndex = 0;
    @Getter
    private final String originalContent;

    /**
     * 构造函数
     *
     * @param content 原始内容，将被分割成块
     */
    public MCPStreamingResponse(String content) {
        this.originalContent = content;
        this.chunks = splitContent(content);
        log.debug("Created StreamingResult with {} chunks", chunks.length);
    }

    /**
     * 构造函数，支持自定义分割
     *
     * @param content 原始内容
     * @param delimiter 分割符
     */
    public MCPStreamingResponse(String content, String delimiter) {
        this.originalContent = content;
        this.chunks = content.split(delimiter);
        log.debug("Created StreamingResult with {} chunks using delimiter: {}", chunks.length, delimiter);
    }

    /**
     * 获取下一个数据块
     *
     * @return 下一个数据块，如果没有更多数据则返回null
     */
    public String getNextChunk() {
        if (currentIndex < chunks.length) {
            String chunk = chunks[currentIndex];
            currentIndex++;

            log.debug("Returning chunk {}/{}: {}", currentIndex, chunks.length,
                    chunk.length() > 50 ? chunk.substring(0, 50) + "..." : chunk);

            return chunk + " "; // 添加空格以保持词与词之间的间距
        }

        log.debug("No more chunks available");
        return null;
    }

    /**
     * 检查是否还有更多数据块
     *
     * @return 如果还有数据块返回true，否则返回false
     */
    public boolean hasMore() {
        return currentIndex < chunks.length;
    }

    /**
     * 检查是否已完成
     *
     * @return 如果所有数据块都已发送返回true，否则返回false
     */
    public boolean isComplete() {
        return currentIndex >= chunks.length;
    }

    /**
     * 获取总块数
     *
     * @return 总块数
     */
    public int getTotalChunks() {
        return chunks.length;
    }

    /**
     * 获取进度百分比
     *
     * @return 进度百分比（0-100）
     */
    public int getProgress() {
        if (chunks.length == 0) {
            return 100;
        }
        return (currentIndex * 100) / chunks.length;
    }

    /**
     * 重置到开始位置
     */
    public void reset() {
        this.currentIndex = 0;
        log.debug("StreamingResult reset to beginning");
    }

    /**
     * 跳到指定位置
     *
     * @param index 目标索引
     */
    public void seekTo(int index) {
        if (index >= 0 && index <= chunks.length) {
            this.currentIndex = index;
            log.debug("StreamingResult seeked to index: {}", index);
        } else {
            log.warn("Invalid seek index: {}, valid range: 0-{}", index, chunks.length);
        }
    }

    /**
     * 获取剩余块数
     *
     * @return 剩余块数
     */
    public int getRemainingChunks() {
        return Math.max(0, chunks.length - currentIndex);
    }

    /**
     * 预览下一个块（不移动指针）
     *
     * @return 下一个块的内容，如果没有则返回null
     */
    public String peekNext() {
        if (currentIndex < chunks.length) {
            return chunks[currentIndex] + " ";
        }
        return null;
    }

    /**
     * 获取所有剩余的块
     *
     * @return 剩余块的数组
     */
    public String[] getRemainingChunksArray() {
        if (currentIndex >= chunks.length) {
            return new String[0];
        }

        String[] remaining = new String[chunks.length - currentIndex];
        System.arraycopy(chunks, currentIndex, remaining, 0, remaining.length);
        return remaining;
    }

    /**
     * 分割内容的私有方法
     *
     * @param content 要分割的内容
     * @return 分割后的数组
     */
    private String[] splitContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new String[]{"Empty content"};
        }

        // 优先按句号分割，保持句子完整性
        String[] sentences = content.split("。");
        if (sentences.length > 1) {
            // 为非最后一个句子添加句号
            for (int i = 0; i < sentences.length - 1; i++) {
                if (!sentences[i].trim().isEmpty()) {
                    sentences[i] = sentences[i].trim() + "。";
                }
            }
            // 过滤空字符串
            return java.util.Arrays.stream(sentences)
                    .filter(s -> !s.trim().isEmpty())
                    .toArray(String[]::new);
        }

        // 如果没有句号，按逗号分割
        String[] phrases = content.split(",|，");
        if (phrases.length > 1) {
            return java.util.Arrays.stream(phrases)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }

        // 如果没有逗号，按空格分割词语
        String[] words = content.trim().split("\\s+");
        if (words.length > 5) { // 如果词语太多，按词分割
            return words;
        }

        // 如果词语较少，保持原内容
        return new String[]{content.trim()};
    }

    @Override
    public String toString() {
        return String.format("StreamingResult{totalChunks=%d, currentIndex=%d, progress=%d%%, complete=%s}",
                chunks.length, currentIndex, getProgress(), isComplete());
    }
}
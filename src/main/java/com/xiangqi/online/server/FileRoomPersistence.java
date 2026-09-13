package com.xiangqi.online.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件型（JSON 落盘）运行态持久化。原子写入 <code>path + ".tmp"</code> 后再移动，
 * 降低进程被中断时损坏已有快照的概率。
 *
 * <p>路径默认取环境变量 <code>XQ_ROOM_STATE_FILE</code>，缺省为 <code>./data/room-state.json</code>。
 */
public final class FileRoomPersistence implements RoomPersistence {
    public static final String DEFAULT_PATH = "./data/room-state.json";

    private final Path filePath;
    private final ObjectMapper mapper;

    public FileRoomPersistence() {
        this(defaultPathFromEnv());
    }

    public FileRoomPersistence(String path) {
        this.filePath = Paths.get(path);
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void save(List<Map<String, Object>> records) {
        try {
            Path parent = filePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(records);
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // 持久化失败不应中断对局主流程；记日志由上层决定。这里静默兜底。
        }
    }

    @Override
    public List<Map<String, Object>> load() {
        if (!Files.exists(filePath)) {
            return new ArrayList<Map<String, Object>>();
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return new ArrayList<Map<String, Object>>();
            }
            List<Map<String, Object>> records = mapper.readValue(
                new String(bytes, StandardCharsets.UTF_8),
                new TypeReference<List<Map<String, Object>>>() { });
            return records == null ? new ArrayList<Map<String, Object>>() : records;
        } catch (IOException ex) {
            return new ArrayList<Map<String, Object>>();
        }
    }

    private static String defaultPathFromEnv() {
        String env = System.getenv("XQ_ROOM_STATE_FILE");
        return env == null || env.trim().isEmpty() ? DEFAULT_PATH : env.trim();
    }
}
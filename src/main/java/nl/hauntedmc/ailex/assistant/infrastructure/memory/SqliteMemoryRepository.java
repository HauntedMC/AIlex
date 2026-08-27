package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** SQLite/WAL implementation used by the default single-server AIlex deployment. */
public final class SqliteMemoryRepository implements MemoryRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS memory_records (
                id TEXT PRIMARY KEY,
                scope TEXT NOT NULL,
                subject_id TEXT NOT NULL,
                relation_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                memory_key TEXT NOT NULL,
                value TEXT NOT NULL,
                confidence REAL NOT NULL,
                salience REAL NOT NULL,
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                first_observed INTEGER NOT NULL,
                last_confirmed INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                supersedes TEXT NOT NULL,
                tags TEXT NOT NULL
            )
            """;
    private static final String UPSERT = """
            INSERT INTO memory_records (
                id, scope, subject_id, relation_id, kind, memory_key, value, confidence, salience,
                source_type, source_id, first_observed, last_confirmed, occurred_at, expires_at, supersedes, tags
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                scope=excluded.scope,
                subject_id=excluded.subject_id,
                relation_id=excluded.relation_id,
                kind=excluded.kind,
                memory_key=excluded.memory_key,
                value=excluded.value,
                confidence=excluded.confidence,
                salience=excluded.salience,
                source_type=excluded.source_type,
                source_id=excluded.source_id,
                first_observed=excluded.first_observed,
                last_confirmed=excluded.last_confirmed,
                occurred_at=excluded.occurred_at,
                expires_at=excluded.expires_at,
                supersedes=excluded.supersedes,
                tags=excluded.tags
            """;

    private final Path databasePath;
    private Connection connection;

    public SqliteMemoryRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public synchronized void initialize() {
        if (connection != null) {
            return;
        }
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute(CREATE_TABLE);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_memory_scope_subject "
                        + "ON memory_records(scope, subject_id, kind, memory_key)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_memory_expiry ON memory_records(expires_at)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_memory_confirmed ON memory_records(last_confirmed)");
            }
        } catch (SQLException | ClassNotFoundException | IOException exception) {
            close();
            throw new IllegalStateException("Could not initialize SQLite assistant memory", exception);
        }
    }

    @Override
    public synchronized List<MemoryRecord> loadActive(long now) {
        requireConnection();
        List<MemoryRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM memory_records WHERE expires_at <= 0 OR expires_at > ? "
                + "ORDER BY last_confirmed DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load assistant memory", exception);
        }
    }

    @Override
    public synchronized List<MemoryRecord> loadTimeline(String subjectId, String relationId, String key, int limit) {
        requireConnection();
        int maximum = Math.clamp(limit, 1, 128);
        String subject = clean(subjectId);
        String relation = clean(relationId);
        String memoryKey = clean(key);
        String sql = "SELECT * FROM memory_records WHERE (? = '' OR subject_id = ?) "
                + "AND (? = '' OR relation_id = ?) AND (? = '' OR memory_key = ?) "
                + "ORDER BY last_confirmed DESC LIMIT ?";
        List<MemoryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, subject);
            statement.setString(2, subject);
            statement.setString(3, relation);
            statement.setString(4, relation);
            statement.setString(5, memoryKey);
            statement.setString(6, memoryKey);
            statement.setInt(7, maximum);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load assistant memory timeline", exception);
        }
    }

    @Override
    public synchronized void upsert(MemoryRecord record) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            bind(statement, record);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not persist assistant memory", exception);
        }
    }

    @Override
    public synchronized void deleteExpiredBefore(long cutoffEpochMillis) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM memory_records WHERE expires_at > 0 AND expires_at < ?"
        )) {
            statement.setLong(1, cutoffEpochMillis);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not compact assistant memory", exception);
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Closing is best effort during plugin shutdown.
        } finally {
            connection = null;
        }
    }

    private void bind(PreparedStatement statement, MemoryRecord record) throws SQLException {
        int index = 1;
        statement.setString(index++, record.id());
        statement.setString(index++, record.scope().name());
        statement.setString(index++, record.subjectId());
        statement.setString(index++, record.relationId());
        statement.setString(index++, record.kind().name());
        statement.setString(index++, record.key());
        statement.setString(index++, record.value());
        statement.setDouble(index++, record.confidence());
        statement.setDouble(index++, record.salience());
        statement.setString(index++, record.sourceType());
        statement.setString(index++, record.sourceId());
        statement.setLong(index++, record.firstObserved());
        statement.setLong(index++, record.lastConfirmed());
        statement.setLong(index++, record.occurredAt());
        statement.setLong(index++, record.expiresAt());
        statement.setString(index++, record.supersedes());
        statement.setString(index, String.join(",", record.tags()));
    }

    private MemoryRecord read(ResultSet result) throws SQLException {
        Set<String> tags = Arrays.stream(result.getString("tags").split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return new MemoryRecord(
                result.getString("id"),
                MemoryScope.valueOf(result.getString("scope")),
                result.getString("subject_id"),
                result.getString("relation_id"),
                MemoryKind.valueOf(result.getString("kind")),
                result.getString("memory_key"),
                result.getString("value"),
                result.getDouble("confidence"),
                result.getDouble("salience"),
                result.getString("source_type"),
                result.getString("source_id"),
                result.getLong("first_observed"),
                result.getLong("last_confirmed"),
                result.getLong("occurred_at"),
                result.getLong("expires_at"),
                result.getString("supersedes"),
                tags
        );
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private void requireConnection() {
        if (connection == null) {
            throw new IllegalStateException("SQLite assistant memory is not initialized");
        }
    }
}

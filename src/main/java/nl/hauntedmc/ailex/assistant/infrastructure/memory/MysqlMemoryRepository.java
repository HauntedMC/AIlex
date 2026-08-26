package nl.hauntedmc.ailex.assistant.infrastructure.memory;

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

/** Shared MySQL implementation for one logical AIlex identity across multiple Paper runtimes. */
public final class MysqlMemoryRepository implements MemoryRepository {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String table;
    private final String changeTable;
    private Connection connection;

    public MysqlMemoryRepository(String jdbcUrl, String username, String password, String tablePrefix) {
        this.jdbcUrl = clean(jdbcUrl);
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        String prefix = clean(tablePrefix).replaceAll("[^A-Za-z0-9_]+", "_");
        String safePrefix = prefix.isBlank() ? "ailex_" : prefix;
        this.table = safePrefix + "memory_records";
        this.changeTable = safePrefix + "memory_changes";
    }

    @Override
    public synchronized void initialize() {
        if (!jdbcUrl.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("AIlex shared memory requires a jdbc:mysql: URL");
        }
        connectAndEnsureSchema();
    }

    @Override
    public synchronized List<MemoryRecord> loadActive(long now) {
        ensureConnection();
        String sql = "SELECT * FROM " + table + " WHERE expires_at <= 0 OR expires_at > ? ORDER BY last_confirmed DESC";
        List<MemoryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            invalidateConnection();
            throw new IllegalStateException("Could not load shared assistant memory", exception);
        }
    }

    @Override
    public synchronized List<MemoryRecord> loadTimeline(String subjectId, String relationId, String key, int limit) {
        ensureConnection();
        String sql = "SELECT * FROM " + table + " WHERE (? = '' OR subject_id = ?) "
                + "AND (? = '' OR relation_id = ?) AND (? = '' OR memory_key = ?) "
                + "ORDER BY last_confirmed DESC LIMIT ?";
        List<MemoryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String subject = clean(subjectId);
            String relation = clean(relationId);
            String memoryKey = clean(key);
            statement.setString(1, subject);
            statement.setString(2, subject);
            statement.setString(3, relation);
            statement.setString(4, relation);
            statement.setString(5, memoryKey);
            statement.setString(6, memoryKey);
            statement.setInt(7, Math.clamp(limit, 1, 128));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            invalidateConnection();
            throw new IllegalStateException("Could not load shared assistant memory timeline", exception);
        }
    }

    @Override
    public synchronized List<MemoryRecord> loadChangedSince(long sinceEpochMillis, int limit) {
        ensureConnection();
        String sql = "SELECT * FROM " + table + " WHERE last_confirmed > ? OR expires_at > ? "
                + "ORDER BY GREATEST(last_confirmed, expires_at) ASC LIMIT ?";
        List<MemoryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceEpochMillis);
            statement.setLong(2, sinceEpochMillis);
            statement.setInt(3, Math.clamp(limit, 1, 2_048));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            invalidateConnection();
            throw new IllegalStateException("Could not load legacy shared assistant memory changes", exception);
        }
    }

    @Override
    public synchronized long latestChangeSequence() {
        ensureConnection();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(seq), 0) FROM " + changeTable)) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (SQLException exception) {
            invalidateConnection();
            throw new IllegalStateException("Could not read shared assistant memory change cursor", exception);
        }
    }

    @Override
    public synchronized List<SharedChange> loadChangesAfter(long sequence, int limit) {
        ensureConnection();
        String sql = "SELECT c.seq AS change_seq, m.* FROM " + changeTable + " c JOIN " + table
                + " m ON m.id = c.record_id WHERE c.seq > ? ORDER BY c.seq ASC LIMIT ?";
        List<SharedChange> changes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, sequence));
            statement.setInt(2, Math.clamp(limit, 1, 2_048));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    changes.add(new SharedChange(result.getLong("change_seq"), read(result)));
                }
            }
            return List.copyOf(changes);
        } catch (SQLException exception) {
            invalidateConnection();
            throw new IllegalStateException("Could not load ordered shared assistant memory changes", exception);
        }
    }

    @Override
    public boolean shared() {
        return true;
    }

    @Override
    public synchronized void upsert(MemoryRecord record) {
        if (record == null) {
            return;
        }
        ensureConnection();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
                bind(statement, record);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + changeTable + " (record_id) VALUES (?)"
            )) {
                statement.setString(1, record.id());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            invalidateConnection();
            throw new IllegalStateException("Could not persist shared assistant memory", exception);
        } finally {
            restoreAutoCommit(originalAutoCommit);
        }
    }

    @Override
    public synchronized void deleteExpiredBefore(long cutoffEpochMillis) {
        ensureConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE expires_at > 0 AND expires_at < ?"
        )) {
            statement.setLong(1, cutoffEpochMillis);
            statement.executeUpdate();
        } catch (SQLException exception) {
            invalidateConnection();
            throw new IllegalStateException("Could not compact shared assistant memory", exception);
        }
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }

    private void connectAndEnsureSchema() {
        closeConnection();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTableSql());
                statement.execute(createChangeTableSql());
                createIndex(statement, "idx_ailex_scope_subject", table, "scope, subject_id, kind, memory_key");
                createIndex(statement, "idx_ailex_expiry", table, "expires_at");
                createIndex(statement, "idx_ailex_confirmed", table, "last_confirmed");
                createIndex(statement, "idx_ailex_memory_key_confirmed", table, "memory_key, last_confirmed");
                createIndex(statement, "idx_ailex_change_record", changeTable, "record_id");
            }
        } catch (SQLException | ClassNotFoundException exception) {
            closeConnection();
            throw new IllegalStateException("Could not initialize shared MySQL assistant memory", exception);
        }
    }

    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connectAndEnsureSchema();
            }
        } catch (SQLException exception) {
            closeConnection();
            throw new IllegalStateException("Could not validate shared MySQL assistant memory connection", exception);
        }
    }

    private String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id VARCHAR(36) PRIMARY KEY,"
                + "scope VARCHAR(32) NOT NULL,"
                + "subject_id VARCHAR(96) NOT NULL,"
                + "relation_id VARCHAR(96) NOT NULL,"
                + "kind VARCHAR(32) NOT NULL,"
                + "memory_key VARCHAR(128) NOT NULL,"
                + "value VARCHAR(512) NOT NULL,"
                + "confidence DOUBLE NOT NULL,"
                + "salience DOUBLE NOT NULL,"
                + "source_type VARCHAR(64) NOT NULL,"
                + "source_id VARCHAR(128) NOT NULL,"
                + "first_observed BIGINT NOT NULL,"
                + "last_confirmed BIGINT NOT NULL,"
                + "occurred_at BIGINT NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "supersedes VARCHAR(36) NOT NULL,"
                + "tags TEXT NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private String createChangeTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + changeTable + " ("
                + "seq BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "record_id VARCHAR(36) NOT NULL,"
                + "created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private String upsertSql() {
        return "INSERT INTO " + table + " (id, scope, subject_id, relation_id, kind, memory_key, value, confidence, "
                + "salience, source_type, source_id, first_observed, last_confirmed, occurred_at, expires_at, "
                + "supersedes, tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE scope=VALUES(scope), subject_id=VALUES(subject_id), "
                + "relation_id=VALUES(relation_id), kind=VALUES(kind), memory_key=VALUES(memory_key), "
                + "value=VALUES(value), confidence=VALUES(confidence), salience=VALUES(salience), "
                + "source_type=VALUES(source_type), source_id=VALUES(source_id), "
                + "first_observed=VALUES(first_observed), last_confirmed=VALUES(last_confirmed), "
                + "occurred_at=VALUES(occurred_at), expires_at=VALUES(expires_at), "
                + "supersedes=VALUES(supersedes), tags=VALUES(tags)";
    }

    private void createIndex(Statement statement, String name, String targetTable, String columns) throws SQLException {
        try {
            statement.execute("CREATE INDEX " + name + " ON " + targetTable + " (" + columns + ")");
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 1061) {
                throw exception;
            }
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
                result.getString("id"), MemoryScope.valueOf(result.getString("scope")),
                result.getString("subject_id"), result.getString("relation_id"),
                MemoryKind.valueOf(result.getString("kind")), result.getString("memory_key"),
                result.getString("value"), result.getDouble("confidence"), result.getDouble("salience"),
                result.getString("source_type"), result.getString("source_id"),
                result.getLong("first_observed"), result.getLong("last_confirmed"),
                result.getLong("occurred_at"), result.getLong("expires_at"), result.getString("supersedes"), tags
        );
    }

    private void rollbackQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The next operation reconnects.
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            invalidateConnection();
        }
    }

    private void invalidateConnection() {
        closeConnection();
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Best effort during shutdown/reconnect.
        } finally {
            connection = null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}

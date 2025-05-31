package com.connorgillmead.chat;

import com.connorgillmead.chat.server.database.Database;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for the Database class.
 * This test ensures that the database can be initialized correctly and that the
 * necessary tables are created.
 * It also checks that the database connection can be established without
 * exceptions.
 */
class DatabaseTest {

    private static final String DB_NAME = "chat.db";

    @BeforeEach
    @AfterEach
    void cleanupDatabaseFile() throws IOException {
        // Delete the database file before and after each test to ensure a clean state.
        Files.deleteIfExists(Paths.get(DB_NAME));
    }

    @Test
    void testDatabaseInitialization() {
        assertDoesNotThrow(() -> {
            Database.chatDatabase();
        }, "Database initialization should not throw an exception.");

        File dbFile = new File(DB_NAME);
        assertTrue(dbFile.exists(), "Database '" + DB_NAME + "' should be created after initialization.");

        try (Connection connection = Database.connection()) {
            assertNotNull(connection, "Database connection should not be null.");
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet rsUsers = metaData.getTables(null, null, "users", null)) {
                assertTrue(rsUsers.next(), "Table 'users' should exist in the database.");
            }

            try (ResultSet rsMessages = metaData.getTables(null, null, "messages", null)) {
                assertTrue(rsMessages.next(), "Table 'messages' should exist in the database.");
            }

            try (ResultSet rsFiles = metaData.getTables(null, null, "files", null)) {
                assertTrue(rsFiles.next(), "Table 'files' should exist in the database.");
            }

            try (ResultSet rsImages = metaData.getTables(null, null, "images", null)) {
                assertTrue(rsImages.next(), "Table 'images' should exist in the database.");
            }

        } catch (SQLException e) {
            fail("Database connection should be established without exceptions: " + e.getMessage());
        }
    }

}

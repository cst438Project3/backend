package com.transferhelper.backend;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SupabaseSchemaMigrationTest {

    @Test
    void migrationFileExists() throws Exception {
        Path path = Path.of("supabase/migrations/20260421213317_init_schema.sql");
        assertTrue(Files.exists(path));
    }

    @Test
    void migrationContainsTables() throws Exception {
        Path path = Path.of("supabase/migrations/20260421213317_init_schema.sql");
        String sql = Files.readString(path).toLowerCase();

        assertTrue(sql.contains("create table"));
        assertTrue(sql.contains("student"));
        assertTrue(sql.contains("course"));
    }
}
-- Deployment metadata only. No users, tokens, routes, or run records are accepted yet.
CREATE TABLE service_metadata (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    application TEXT NOT NULL CHECK (application = 'WAYiRUN'),
    schema_version INTEGER NOT NULL CHECK (schema_version >= 1)
);
INSERT INTO service_metadata (singleton, application, schema_version) VALUES (1, 'WAYiRUN', 1);

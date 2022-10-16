{{b QUERY}}CREATE{{v TEMPORARY}}{{/v}} TABLE {{v TABLE/}} ({{v COLUMNS/}}{{v PRIMARY_KEYS}}{{/v}}{{v FOREIGN_KEYS}}{{/v}}{{v UNIQUE_CONSTRAINTS}}{{/v}}{{v CHECKS}}{{/v}}){{/b}}
{{b SEPARATOR}}, {{/b}}
{{b TEMPORARY}} TEMPORARY{{/b}}
{{b COLUMN}}{{v NAME/}} {{v TYPE/}}{{v DEFAULT}}{{/v}}{{v NULLABLE}}{{/v}}{{v CUSTOM_ATTRIBUTES}}{{/v}}{{/b}}
{{b NULL}} NULL{{/b}}
{{b NOTNULL}} NOT NULL{{/b}}
{{b DEFAULT}} DEFAULT {{v V/}}{{/b}}
{{b CUSTOM_ATTRIBUTES}} {{v V/}}{{/b}}
{{b PRIMARY_KEY}}{{v PRIMARY_KEY_NAME}}{{/v}}PRIMARY KEY ({{v COLUMN_NAMES/}}){{/b}}
{{b FOREIGN_KEY}}{{v FOREIGN_KEY_NAME}}{{/v}}FOREIGN KEY ({{v LOCAL_COLUMN_NAMES/}}) REFERENCES {{v FOREIGN_TABLE/}} ({{v FOREIGN_COLUMN_NAMES/}}){{v VIOLATION_ACTIONS}}{{/v}}{{b ON_DELETE}} ON DELETE {{v ON_DELETE_ACTION/}}{{/b}}{{b ON_UPDATE}} ON UPDATE {{v ON_UPDATE_ACTION/}}{{/b}}{{/b}}
{{b UNIQUE_CONSTRAINT}}{{v UNIQUE_CONSTRAINT_NAME}}{{/v}}UNIQUE ({{v COLUMN_NAMES/}}){{/b}}
{{b CHECK}}{{v CHECK_NAME}}{{/v}}CHECK ({{v EXPRESSION/}}){{/b}}
{{b PRIMARY_KEY_NAME}}CONSTRAINT {{v NAME/}} {{/b}}
{{b FOREIGN_KEY_NAME}}CONSTRAINT {{v NAME/}} {{/b}}
{{b UNIQUE_CONSTRAINT_NAME}}CONSTRAINT {{v NAME/}} {{/b}}
{{b CHECK_NAME}}CONSTRAINT {{v NAME/}} {{/b}}
{{b ON_UPDATE_NOACTION}}NO ACTION{{/b}}
{{b ON_UPDATE_RESTRICT}}RESTRICT{{/b}}
{{b ON_UPDATE_CASCADE}}CASCADE{{/b}}
{{b ON_UPDATE_SETNULL}}SET NULL{{/b}}
{{b ON_UPDATE_SETDEFAULT}}SET DEFAULT{{/b}}
{{b ON_DELETE_NOACTION}}NO ACTION{{/b}}
{{b ON_DELETE_RESTRICT}}RESTRICT{{/b}}
{{b ON_DELETE_CASCADE}}CASCADE{{/b}}
{{b ON_DELETE_SETNULL}}SET NULL{{/b}}
{{b ON_DELETE_SETDEFAULT}}SET DEFAULT{{/b}}

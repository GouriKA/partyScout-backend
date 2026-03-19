CREATE TABLE venue_enrichment (
    place_id        VARCHAR(500) PRIMARY KEY,
    age_range_min   INT,
    age_range_max   INT,
    persona_tags    VARCHAR(1000),   -- comma-separated e.g. "child,teen,youngAdult"
    under18_welcome BOOLEAN,
    requires_adult  BOOLEAN,
    alcohol_premises BOOLEAN,
    last_enriched   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

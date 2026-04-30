# Skip non-idempotent migrations when import version is unknown

When a user imports alerts from the UI and the JSON has no version envelope, `AlertMigrator` treats the source version as unknown rather than assuming the oldest possible version ("0.0.0"). In that case, only idempotent migrations run (currently ≥ 2.13.0); non-idempotent early migrations (2.4.0 gain scaling, 2.8.0 flash property reset) are skipped.

The alternative — running all migrations on unknown-version data — would silently corrupt audio gain values and flash settings on any post-v2 alert import, since those migrations overwrite fields unconditionally. v2 alerts (the only ones that genuinely need those migrations) are effectively extinct in the wild. The safer failure mode is "old v2 alert is not fully migrated" over "v3/v4 alert has its settings corrupted silently."

## Consequences

Any migration added in future must be classified at the call site in `AlertMigrator.migrate()`: either it is safe to run on already-migrated data (no `versionKnown` guard needed) or it is not (add the guard and document why). This is a required step when adding a migration — not optional hygiene.

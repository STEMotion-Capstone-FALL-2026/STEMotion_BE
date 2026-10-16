# AI usage and configured cost estimates

The BE records actual provider model and token usage in M01. Existing columns `model`, `input_tokens`, `output_tokens` and `monetary_cost` are reused; API names are `tokensIn`, `tokensOut`, and `estimatedCostUsd`. Unknown usage/cost remains null, never a fabricated zero.

## Configuration

Set `AI_PRICING_JSON` to an object keyed by the exact model name returned by the provider. Each entry requires nonnegative `inputPerMillion`, `outputPerMillion` and a nonempty `version`. Default `{}` means pricing is unknown. Use verified current pricing supplied by the operator; there is no embedded price table. Example for tests only (not a current price recommendation):

```json
{"fixture-priced":{"inputPerMillion":1.5,"outputPerMillion":2.5,"version":"local-test-v1"}}
```

Cost = (input tokens * input rate + output tokens * output rate) / 1,000,000, rounded to 10 decimal places. Missing model/rates/either token count means unknown cost. A measured zero with configured rates is zero. Each completed audit row snapshots the rate version and both rates, so later configuration changes do not rewrite history. This is a nominal estimate, not a provider invoice; cached-token discounts, other modalities and Whisper compute are not calculated.

## Admin API

`GET /api/v1/admin/ai-usage` requires an ADMIN JWT. Query parameters: `from` / `to` (inclusive ISO dates), optional exact `model` / `userId`, `page` (0-based, default 0), `size` (default 20, max 100). Default interval is the last 30 calendar days; maximum interval is 366 days. Day boundaries use `AI_USAGE_ZONE` (default `Asia/Ho_Chi_Minh`).

Results contain content, page, size, totalElements, totalPages, from, to and timezone for day/model/user groups, ordered by day descending then model/user. Pagination counts groups, not individual requests. Rows expose request and failure counts, token sums, `unknownTokenRequests`, `unknownCostRequests`, `knownEstimatedCostUsd` (known USD subtotal), and `estimatedCostUsd` (null if any request has unknown/non-USD cost). Partial token sums must be read alongside coverage counts. SQL performs aggregation and pagination; request payloads are not returned. FE dashboard implementation is separate; existing overview behavior is unchanged.

## V18 rollout and rollback

`V18__ai_pricing_provenance.sql` adds three nullable pricing-provenance columns, a consistency constraint and a usage index. It preserves historical unknown costs. V12-V14 are unchanged. Cloud automatic Flyway remains disabled. V18 was tested only on a disposable local fixture; it has NOT been applied to production.

For a separately approved production rollout: back up the database, verify current schema/version, review V18 against that schema, apply with the team's manual migration procedure, verify columns/index/constraints and deploy matching application code. This document does not authorize that rollout. Hibernate schema validation requires V18 before running the new code against that database.

`docs/rollback/V18__ai_pricing_provenance.down.sql` refuses to drop columns once any pricing provenance has been recorded. Archive those values and obtain a separate rollback decision before removing populated columns. Existing token/cost columns are retained.

Validation: mocked provider tests, Java calculator/RBAC tests, PostgreSQL integration tests for timezone grouping, pagination, user separation and persisted provenance, plus a PGlite migration/guarded-rollback test. No paid API or production database is involved.

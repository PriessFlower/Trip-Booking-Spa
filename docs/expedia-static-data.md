# Expedia static data

## Status: the ingestion pipeline was retired on 2026-09-08

Static content is owned by `trip-booking-agg`, not by this gateway. What used to live here —
Property Content / Catalog file / Inactive Property ingestion, the geography (regions) build-up,
and the transform that fanned a snapshot out into a restored legacy catalog — has been removed
together with the six tables it wrote.

Why, in one line: the six catalog tables had **zero readers** anywhere in the repo (bff and b2b
referenced none of them), their data had been frozen since 2026-08-14 because both `@Scheduled`
jobs were `enabled: false` in Nacos the whole time, and they still occupied about 3 GB of a 29 GB
database. Aggregation and canonical identity belong to the aggregation domain (`docs/product-identity.md`
R-2.4), and agg already runs `hotel_base` / `room_base` / `room_i18n` in production.

Removed in that change:

| Removed | What it was |
| --- | --- |
| `hotel_details`, `room_base`, `hotel_picture`, `hotel_extend` | restored legacy catalog domain, written only by the transform |
| `country_info`, `city_info` | geography build-up from the Regions API |
| `supplier_hotel_base.hotel_id` / `.merger`, `supplier_room_base.room_id` / `.merger` | unified-side columns; production held 97,409/97,409 and 356,571/356,571 rows where they merely copied the supplier-side id |
| `task.expedia-hotel-sync`, `task.expedia-remove-hotel` | the two daily jobs, never enabled in production |
| `supplier.expedia.static-data-enabled`, `supplier.expedia.static-data.*` | the ingestion gate and its batch/download settings |

`StaticCatalogRetiredArchRulesTest` keeps all of that from creeping back, the way
`ProductIdentityArchRulesTest` guards the retired `global_product_supplier` bridge.

## What is still here

`expedia_property_content` — the raw + normalized property snapshot, 194,548 rows
(97,410 `en-US`, 97,138 `zh-CN`), frozen at 2026-08-14. It is **read-only now**; nothing in this
repo writes it any more. Its readers are:

- `bff/store/PropertyContentRepo` — hotel detail pages, city listing, and the `LIKE` fallback used
  when the agg suggest endpoint is unavailable;
- `b2b/service/B2bBookingService` — property summary on the B2B booking path;
- `ExpediaProductKeyDeriver` — room/rate facts for productKey derivation.

Refreshing that content is agg's job. If this repo ever needs fresh Expedia content again, the
ingestion code is still in git history at commit `b692544b` — but re-adding it here is a decision
about who owns static content, not a revert.

`ExpediaRegionService` also survives: `/query/expediaHotelIdByCity` is a registered SPA contract
endpoint (pinned by `SpaControllerContractTest`). It asks Rapid Geography per request and stores
nothing.

## Safety boundary (unchanged)

- Rapid test endpoint only by default.
- Booking stays disabled (`expedia.booking-enabled=false`); startup fails if it is enabled against
  the production endpoint.
- Production API access is blocked unless the separately controlled production flag is enabled.
- Credentials come from `EXPEDIA_API_KEY` / `EXPEDIA_SHARED_SECRET`; portal credentials belong in
  neither this repository nor application configuration.
- Raw API bodies and authorization headers must not be logged.

## Backups taken when the tables were dropped

- Structure of all eight affected tables: `SHOW CREATE TABLE` output attached to the retirement commit.
- `country_info` + `city_info` data (the only rows not recomputable from the retained snapshot):
  `trip-offline:/opt/trip-booking-spa/retired-static/geo-info-20260908.sql.gz`,
  md5 `faaac920097cae16c58792e5b62692dd`, 630,145 bytes.

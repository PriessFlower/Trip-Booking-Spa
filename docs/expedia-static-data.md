# Expedia static data integration

## Scope and safety boundary

- Rapid test endpoint only by default.
- Booking remains disabled (`expedia.booking-enabled=false`) and startup fails if it is enabled.
- Production API access is blocked unless a separately controlled production flag is explicitly enabled.
- API credentials are read from `EXPEDIA_API_KEY` and `EXPEDIA_SHARED_SECRET`.
- Portal credentials do not belong in this repository or application configuration.
- Raw API bodies and authorization headers must not be logged.
- Static ingestion is opt-in through the Nacos key `supplier.expedia.static-data-enabled` (default `false`); enabling it without credentials fails startup. The value is bound at startup only, so a container restart is required after changing it.

## Ingestion flow

1. Use the Property Catalog File to seed the active property ID set and high-level mapping candidates.
2. Fetch Property Content in batches of at most 250 property IDs.
3. Keep the raw property JSON, calculate its SHA-256, and map a normalized document.
4. Upsert the raw snapshot, normalized document, and evidence into `expedia_property_content`.
5. For updates, query with `date_updated_start` and `date_updated_end` and replace the whole property document.
6. For additions, query with `date_added_start` and `date_added_end`, normally partitioned by country.
7. Mark properties returned by Inactive Property as inactive. Run this at least every two weeks.

The Workshop recommends running new/updated deltas at least weekly. Content responses return the
complete current property and do not identify which section changed, so partial patching is not used.

## Field mapping

| Normalized field | Rapid Content source | Notes |
| --- | --- | --- |
| `supplierPropertyId` | `property_id` | Expedia ID and primary key |
| `name` | `name` | Locale depends on request language |
| `address` | `address` | Keeps country, city, postal code and obfuscation flag |
| `coordinates` | `location.coordinates` | Keeps the location obfuscation flag |
| `rating` | `ratings.property`, `ratings.guest` | Property and guest ratings stay separate |
| `category` | `category` | Expedia category ID and label |
| `chain`, `brand` | `chain`, `brand` | IDs and labels are retained |
| `businessModel` | `business_model` | Expedia Collect and Property Collect stay separate |
| `stayInformation` | `checkin`, `checkout`, `fees`, `policies` | Raw HTML text is retained in the normalized JSON |
| `propertyAmenities` | `amenities` | Level is explicitly `PROPERTY` |
| `rooms` | `rooms` | Includes occupancy, area, beds, room images and room amenities |
| `ratePlans` | `rates` | Rate-plan amenities are not flattened into room/property amenities |
| `sourceAddedAt`, `sourceUpdatedAt` | `dates` | Drives delta synchronization |

Every normalized document contains evidence with `source=EXPEDIA`, property ID, fetch time, raw
SHA-256, mapping version and field-level JSON paths. Internal hotel matching remains intentionally
unset until a dedicated matching algorithm produces an internal ID, confidence and match evidence.

## Local database

Create the snapshot table once:

```bash
docker exec -i tg-local-mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < config/mysql/expedia-static-schema.sql
```

Real passwords should be supplied by the local environment rather than copied into scripts.

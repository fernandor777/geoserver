# Smart Data Loader

## Metadata Cache REST Endpoints

The module exposes REST endpoints to manually refresh/invalidate the in-memory JDBC metadata cache.

Base path:

`/rest/smartdataloader/metadata/cache`

### 1. Refresh cache for one datastore

Invalidates cache entries for a specific source datastore (and optionally a specific schema).

- Method: `POST`
- URL: `/rest/smartdataloader/metadata/cache/refresh`
- Query parameters:
  - `storeId` (required): GeoServer datastore id used by Smart Data Loader.
  - `schema` (optional): schema name to invalidate; if omitted, the datastore schema is used.

Example:

```bash
curl -u admin:geoserver -X POST \
  "http://localhost:8080/geoserver/rest/smartdataloader/metadata/cache/refresh?storeId=<STORE_ID>&schema=public"
```

Typical JSON response:

```json
{
  "status": "ok",
  "action": "refreshed",
  "scope": "<STORE_ID>"
}
```

If cache is disabled, response is still `200` with:

```json
{
  "status": "ok",
  "action": "cache-disabled",
  "scope": "<STORE_ID>"
}
```

### 2. Clear all metadata cache entries

Clears the whole Smart Data Loader metadata cache.

- Method: `DELETE`
- URL: `/rest/smartdataloader/metadata/cache`

Example:

```bash
curl -u admin:geoserver -X DELETE \
  "http://localhost:8080/geoserver/rest/smartdataloader/metadata/cache"
```

Typical JSON response:

```json
{
  "status": "ok",
  "action": "cleared",
  "scope": "all"
}
```

### Notes

- These endpoints use the same Spring-managed cache bean used during metadata loading.
- Admin credentials are required (standard GeoServer REST security rules apply).
- Cache behavior can be configured via:
  - `smartdataloader.metadata.cache.enabled`
  - `smartdataloader.metadata.cache.ttl.seconds`
  - `smartdataloader.metadata.cache.max.entries`
  - `smartdataloader.metadata.cache.cleanup.interval.seconds`

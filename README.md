# similar-products

Service behind `GET /product/{productId}/similar`. It asks the product API which products are
similar to a given one, fetches each of their details in parallel, and returns them **in the
ranking the API gave**.

Java 21 · Spring Boot 4.0.8 · Gradle · Caffeine

---

## Quick start

The whole stack — mock product API, this service, Grafana, InfluxDB and k6 — runs with one command.
No local JDK needed.

```bash
docker compose up -d --build

curl localhost:5000/product/1/similar
# [{"id":"2","name":"Dress","price":19.99,"availability":true},
#  {"id":"3","name":"Blazer","price":29.99,"availability":false},
#  {"id":"4","name":"Boots","price":39.99,"availability":true}]

docker compose down
```

| Service | Port |
|---|---|
| this service | 5000 |
| mock product API (`simulado`) | 3001 |
| Grafana | 3000 |
| InfluxDB | 8086 |

`shared/` holds the mock definitions, k6 script and Grafana provisioning, copied from the technical
test's `backendDevTest` repo so this stack is self-contained. Re-copy it if that repo changes.

### Running without Docker

Needs JDK 21 and the mock API up on 3001:

```bash
docker compose up -d simulado
./gradlew bootRun
```

---

## How it works

```
GET /product/1/similar
  └─ findSimilarProductIds("1")        → ["2","3","4"]      cached 5 min
       └─ for each id, concurrently:                            cached 10 s
            findProductDetail(id)
  └─ join in submission order, drop any child that failed
  └─ 200, ranked list
```

One request to a product with N similar products becomes 1 + N calls to the product API. Everything
below exists to keep that fan-out cheap and bounded.

## Design decisions

| Decision | Why |
|---|---|
| Virtual threads for request handling | The work is blocking I/O against a pathologically slow API. Virtual threads remove request-thread sizing as a concern entirely. |
| **Fixed** fan-out pool, not virtual | The pool size *is* the ceiling on product calls in flight. Children beyond it queue instead of each opening a connection. |
| Children fail independently | One dead product shouldn't fail the whole request. A child that errors is logged and dropped; the rest still return. |
| Join in submission order, not completion order | The caller asked for a ranking. Collecting as answers arrive returns the same products wrongly shuffled. |
| Two cache TTLs: 5 min for IDs, 10 s for details | Rankings change rarely; prices and availability change often. One TTL for both is always wrong for one of them. |
| `sync = true` on `@Cacheable` | Locks the key so concurrent first-callers queue instead of all missing and stampeding the API — the exact failure a cache is meant to prevent. |
| 1 s connect / 2 s read timeout | A hung socket can never pin a request thread. The mock's worst call is ~50 s, so an unbounded read would hold threads far past any useful answer. |
| `factory: simple`, not auto-detected | Detection resolves to the JDK HttpClient, whose timeouts surface as an opaque `RestClientException`. The simple factory raises `ResourceAccessException: Read timed out` — the taxonomy is the reason, measured against a server that sends headers then stalls the body. |
| No retries | Those failures are permanent: a 404 that never becomes a 200, a 500 on `/product/6`. A retry only spends the remaining budget on an answer that cannot change. |
| 404 only for an unknown product | The similar-id lookup is the one call with no fallback — without it there is no answer at all. |
| No circuit breaker | Deliberate scope decision. It would protect a sustained outage, but it also adds a moving part and a tuning surface this service does not currently need. See *Known trade-offs*. |

## Configuration

Everything is overridable via environment variable using Spring relaxed binding.

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `api.products.base-url` | `API_PRODUCTS_BASE_URL` | `http://localhost:3001` | Product API base URL. **Inside a container this must be `http://simulado:80`** — `localhost` there is this service itself. |
| `api.similar.max-concurrent-product-api-calls` | `API_SIMILAR_MAX_CONCURRENT_PRODUCT_API_CALLS` | 64 | Fan-out pool size = ceiling on concurrent product calls |
| `server.port` | `SERVER_PORT` | 5000 | |

Cache TTLs and size are in `CacheConfig` (`productDetail` 10 s, `similarIds` 5 min, 10 000 entries).

## Tests

```bash
./gradlew test
```

16 tests. The caching tests are behaviour-level — they assert on what the client returns across
repeated calls, not on annotations being present, so removing `@Cacheable` fails them.

## Load test

Uses the technical test's k6 script unmodified. It reaches the app through
`host.docker.internal:5000`, which is why the service still publishes port 5000 in Docker.

```bash
docker compose run --rm k6 run scripts/test.js
```

Last run on this machine: 11 347 iterations at 126/s, `http_req_duration` avg 19 ms / p95 52 ms, and
all five checks (`normal`, `notFound`, `error`, `slow`, `verySlow`) passing. Treat the numbers as
indicative, not a benchmark.

---

## Known trade-offs

**Total degradation looks like success.** If every child fails, the endpoint returns `200` with an
empty list — indistinguishable from "this product genuinely has no similar products". Failing loudly
would mean a `503`, which is a product decision rather than a technical one, and it was left
undecided rather than assumed.

**No protection against a sustained product-API outage.** Without a breaker, every inbound request
still fans out and every child still fails. The 2 s read timeout bounds how long that takes, but it
does not stop the load. This is the cost of the decision above.

**A restart empties both caches.** They are in-memory only. A 5-minute ID cache surviving restarts
would need a shared store, which is not worth the operational surface at this scale.

**`shared/` can drift** from the `backendDevTest` originals it was copied from.

## Layout

```
src/main/java/com/nunegal/
  SimilarProductsApplication.java      enables caching
  cache/CacheConfig.java               two TTLs, one cache manager
  clients/config/ApiClientConfig.java  RestClient scoped to the product API
  clients/productcatalog/              product API access (cached)
  controller/SimilarProductsController.java
  domain/ProductDetail.java
  exception/                           404 translation
  service/SimilarProductsService.java  fan-out, degradation, ordering
```
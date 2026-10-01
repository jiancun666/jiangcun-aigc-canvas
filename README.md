# semple-aigc-canvas

Java 21 / Spring Boot 3.3 / Spring Cloud 2023 Maven multi-module scaffold based on the layering of `zhi-hub`.

```text
semple-aigc-canvas
|-- semple-aigc-canvas-api
|   |-- semple-aigc-canvas-system-api
|   |-- semple-aigc-canvas-file-api
|   |-- semple-aigc-canvas-job-api
|   `-- semple-aigc-canvas-aigc-api
|-- semple-aigc-canvas-auth
|-- semple-aigc-canvas-common
|   |-- semple-aigc-canvas-common-core
|   |-- semple-aigc-canvas-common-feign
|   |-- semple-aigc-canvas-common-redis
|   |-- semple-aigc-canvas-common-security
|   `-- semple-aigc-canvas-common-web
|-- semple-aigc-canvas-gateway
`-- semple-aigc-canvas-modules
    |-- semple-aigc-canvas-system
    |-- semple-aigc-canvas-file
    |-- semple-aigc-canvas-job
    `-- semple-aigc-canvas-aigc
```

The scaffold retains Maven aggregation, reusable common infrastructure, the gateway authentication filter, service entry points, and the Nacos, logging, and deployment configuration layout.

Copied user, points, model-generation, and file-upload controllers, services, mappers, DTOs, SQL, and business tests were removed. API and service modules remain as clean extension boundaries.

Passwords and keys are represented only by environment-variable placeholders. Inject real values through the environment or configuration center before deployment.

## Build

Install JDK 21 and Maven 3.9+, then run:

```bash
mvn clean verify
```

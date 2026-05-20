# MCMP Web Application

MCMP (Munich Cloud Management Platform) allows users to independently manage the virtual servers assigned to them.
AWX jobs can be controlled through an authorization model based on application services and change groups from ServiceNow.
This enables users to independently order, modify, or restart virtual servers, databases, or load balancers.


## Architecture
- `mcmp-backend/`: Java Spring Boot API. DB migrations via Flyway. MapStruct + Lombok for DTOs.
- `mcmp-frontend/`: Vue.js frontend application.
- `mcmp-eai-*/`: Lightweight Go connectors to various external systems.
- `mcmp-callback-server/`: Go callback consumer.

## Quick Start
You can start the complete stack locally using containers.

To run the application:
1. Start the API Gateway and infrastructure containers from `stack/podman-compose.yml` (e.g., `podman-compose -f stack/podman-compose.yml up refarch-gateway`).
2. Start the Frontend dev server (`npm run dev` in `mcmp-frontend`).
3. Start the Backend API server with the `podman` profile (`mvn clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=podman"`).

## Development
- **Infrastructure:** `cd stack; podman-compose up`
- **Backend:** `cd mcmp-backend; mvn clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=podman"`
- **Frontend:** `cd mcmp-frontend; npm install && npm run dev`

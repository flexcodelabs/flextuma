# Flextuma

Flextuma is a Spring Boot application designed to handle financial transactions and integrations. It serves as a backend service for the FlexCodeLabs payment ecosystem.

## Prerequisites

Before running the application, ensure you have the following installed:

*   **Java 17**: This project requires JDK 17.
*   **Docker & Docker Compose**: For containerization and running dependencies.
*   **Gradle**: The project uses Gradle for build automation (a wrapper is provided).

### External Dependencies

The application relies on PostgreSQL and Redis. By default, the `compose.yaml` does not provision these services; it expects you to provide their connection details via environment variables.

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd flextuma
    ```

2.  **Configuration:**
    Create a `.env` file in the root directory or export these variables in your shell.

    | Variable | Required | Description | Example |
    | :--- | :--- | :--- | :--- |
    | `SPRING_DATASOURCE_URL` | Yes | Database JDBC URL | `jdbc:postgresql://host:5432/db` |
    | `SPRING_DATASOURCE_USERNAME` | Yes | Database username | `postgres` |
    | `SPRING_DATASOURCE_PASSWORD` | Yes | Database password | `secret` |
    | `SPRING_DATA_REDIS_HOST` | Yes | Redis hostname | `redis` |
    | `SPRING_DATA_REDIS_PORT` | No | Redis port (default: 6379) | `6379` |
    | `HIKARI_MAX_POOL` | No | Max JDBC pool size (default: 10) | `10` |

3.  **Build the application:**
    Use the provided Gradle wrapper to build the project.
    ```bash
    ./gradlew clean build -x test
    ```
    *(Note: `-x test` skips tests for a quicker build. Removing it runs the test suite.)*

4.  **Run with Docker Compose:**
    Build and start the application container.
    ```bash
    docker compose up --build
    ```
    *Ensure your database and Redis are reachable from the container.*

The application will be accessible at `http://localhost:8080`.

## Development

To run tests:
```bash
./gradlew test
```

To run the application locally without Docker (requires local Postgres/Redis or port forwarding):
```bash
./gradlew bootRun
```

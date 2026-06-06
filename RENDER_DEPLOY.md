# Docker & Render Deployment Guide for Spring Boot

This guide covers the core concepts of Docker, how our Docker configuration files are structured, and step-by-step instructions to deploy this Spring Boot (Thymeleaf + JPA + PostgreSQL) application to **Render**.

---

## 1. What is Docker?

In traditional software development, running an application on different machines (developers' laptops, testing servers, cloud production) often leads to issues due to environment differences:
> *"But it works on my machine!"*

Docker solves this by packaging an application and all its dependencies into an isolated container.

### Core Concepts:
- **Dockerfile**: A text document containing commands a user can run in the command line to assemble a Docker image. It is the "recipe" for your container.
- **Docker Image**: A read-only template that contains the application code, runtime environment (JRE), library files, environment variables, and config files. Think of it as a **Class** in Java.
- **Docker Container**: A running, isolated instance of a Docker image. Think of it as an **Object (Instance)** of a Class in Java.
- **Docker Hub / Registry**: A repository where Docker images are stored and shared. Render pulls images from here or builds them directly from your Git repository.

---

## 2. Dockerfile Walkthrough

Our project has a **multi-stage `Dockerfile`** located in the root of the project (`dockertest/Dockerfile`). 

### Why Multi-stage?
Instead of creating a single massive image that contains the Maven compiler, test libraries, and JDK source code, we split the process into two stages:
1. **Build Stage**: Compiles the Java project. This requires a full JDK and Maven.
2. **Run Stage**: Runs the compiled JAR file. This only requires a lightweight JRE (Java Runtime Environment).

This keeps the final production image small (under 200MB instead of 800MB+), secure, and fast to download.

### Line-by-Line Breakdown:
```dockerfile
# STAGE 1: Build the Maven project inside a Linux container.
FROM maven:3.9.6-eclipse-temurin-17 AS builder
```
- `FROM`: Starts a new build stage and selects the official Maven image containing Java 17 as the base environment. We alias this stage as `builder`.
- Building inside a container avoids CRLF (carriage return line feed) scripting errors when running Maven scripts created on Windows inside Linux containers.

```dockerfile
WORKDIR /app
```
- `WORKDIR`: Sets the working directory inside the container's virtual filesystem to `/app`. Any subsequent command will execute here.

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B
```
- `COPY`: Copies the local `pom.xml` dependency manifest into the container.
- `RUN`: Runs `mvn dependency:go-offline` to download all project dependencies before copying the code. This utilizes Docker’s layer cache; if dependencies don't change, Docker skips this step in future builds.

```dockerfile
COPY src ./src
RUN mvn package -DskipTests -B
```
- Copies the actual source code (`src` folder) into the container.
- Compiles, tests, and packages the app into a `.jar` file under `/app/target/`.

```dockerfile
# STAGE 2: Lightweight Run Stage using JRE (Java Runtime Environment)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
```
- Starts a fresh, clean stage using a minimal Eclipse Temurin JRE (Runtime only, no compiler) on top of Ubuntu Jammy.

```dockerfile
COPY --from=builder /app/target/*.jar app.jar
```
- `COPY --from=builder`: Copies the compiled JAR file from the `/app/target` directory of the `builder` stage into our current run stage. This leaves behind all Maven caching and compiler packages.

```dockerfile
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- `EXPOSE 8080`: Documents that our containerized app listens on port 8080.
- `ENTRYPOINT`: Defines the command that executes when the container starts up. Here, it runs `java -jar app.jar`.

---

## 3. Local Multi-container Orchestration (Docker Compose)

Running an application that depends on a database (like PostgreSQL) requires starting two separate containers. **Docker Compose** lets us define and run multi-container applications in a single command.

Our config is defined in `compose.yaml`:
```yaml
services:
  postgres:
    image: 'postgres:15-alpine'
    environment:
      - 'POSTGRES_DB=mydatabase'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=myuser'
    ports:
      - '5432:5432'

  app:
    build: .
    ports:
      - '8080:8080'
    environment:
      - 'SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mydatabase'
      - 'SPRING_DATASOURCE_USERNAME=myuser'
      - 'SPRING_DATASOURCE_PASSWORD=secret'
    depends_on:
      - postgres
```

### Key Elements of compose.yaml:
- **`services`**: Defines the containers to start (`postgres` and `app`).
- **`ports` (`host:container`)**: Maps the container port to your machine's port. E.g., `8080:8080` lets you open `http://localhost:8080` in your web browser.
- **`environment`**: Inject environment variables into the container.
- **`depends_on`**: Declares that `app` depends on `postgres`, ensuring the database starts up first.
- **Service Networking**: Docker Compose connects both containers to a private network. In the `app` container, we connect to the database using the hostname `postgres` instead of `localhost`: `jdbc:postgresql://postgres:5432/mydatabase`.

---

## 4. Deploying to Render (Cloud)

Render is a modern cloud hosting platform. It is excellent for deploying Dockerized applications for free or at very low cost.

Here are the step-by-step instructions to deploy this Spring Boot app:

### Step A: Push to GitHub
1. Create a new repository on GitHub (e.g., `dockertest`).
2. Push your code. Make sure that the folder structure contains the `dockertest` subdirectory with the `Dockerfile` and `pom.xml`.

### Step B: Identify Your Database
You have two options for your PostgreSQL database on Render:
1. **Option 1 (Neon PostgreSQL - Recommended)**:
   - Use the database you provided:
     `postgresql://neondb_owner:npg_UiY5nTrAclD2@ep-fragrant-field-ap8wb8z4-pooler.c-7.us-east-1.aws.neon.tech/neondb`
   - Converting this to a JDBC URL yields:
     `jdbc:postgresql://ep-fragrant-field-ap8wb8z4-pooler.c-7.us-east-1.aws.neon.tech/neondb?sslmode=require`
2. **Option 2 (Render PostgreSQL)**:
   - In Render, click **New +** -> **PostgreSQL**.
   - Fill in a name and click **Create Database**.
   - Copy the **External Database URL**.

### Step C: Create the Web Service in Render
1. Log in to [Render Dashboard](https://dashboard.render.com).
2. Click **New +** in the top right, and select **Web Service**.
3. Link your GitHub account and select your repository.

### Step D: Web Service Configurations
Under the web service details page, configure these settings:
- **Name**: `cloud-deploy-hub`
- **Region**: Select a region close to your database (for Neon AWS US-East-1, choose `Oregon (US West)` or `Ohio (US East)`).
- **Branch**: `main` (or whatever branch you pushed to).
- **Root Directory**: `dockertest` *(IMPORTANT: This is because your source code lives in a subdirectory. Render will look for Dockerfile and pom.xml here).*
- **Runtime**: `Docker` *(Render will automatically build your image using the `Dockerfile` in the root directory).*
- **Instance Type**: Select the **Free** tier.

### Step E: Configure Environment Variables
1. Scroll down and click **Advanced**.
2. Click **Add Environment Variable** to add the database connection credentials. This overrides the default properties in `application.properties`:

| Key | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-fragrant-field-ap8wb8z4-pooler.c-7.us-east-1.aws.neon.tech/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | `npg_UiY5nTrAclD2` |

3. Click **Create Web Service**.

### Step F: Deploy and Verify
1. Render will fetch your code from GitHub and start building the container using your Dockerfile. You can watch the build logs in the Render console.
2. Once the build finishes, Render will launch the container and display a status of **Live**.
3. Render will provide you with a public URL (e.g., `https://cloud-deploy-hub.onrender.com`).
4. Click the URL. You should see the dashboard with your active services, and you can interactively add new deployment records into the Neon database!

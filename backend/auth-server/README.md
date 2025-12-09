# Auth Server

A lightweight **Spring Boot authentication server** with user registration, login, and chat server assignment. Supports master/slave failover for chat servers.

---

## Features

- **User Auth**: Register and login with username/password.
- **Server Assignment**: Round-robin assignment to chat server pairs.
- **Failover**: Automatically promotes slave if master goes down.
- **CORS Enabled**: Works with frontend at `http://localhost:5173`.
- **Local Persistence**: Stores data in JSON files (`users.json`, `servers.json`, `userAssignments.json`).

---

## Endpoints

### Authentication
- `POST /auth/register` – Register a new user
- `POST /auth/login` – Login and get assigned chat server
- `GET /auth/users` – List all users
- `GET /auth/resolve/{username}` – Get active chat server for a user

### Server Management (Admin)
- `GET /auth/servers` – List all server pairs
- `POST /auth/servers/add` – Add a new server pair

---

## Run

Start the server with Maven:

```bash
mvn spring-boot:run
```


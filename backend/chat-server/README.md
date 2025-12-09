# Chat Server

A lightweight **Spring Boot chat server** that provides real-time messaging, timeline posting, and follow/unfollow updates. It communicates with a separate DB service for data storage and uses WebSockets for live updates.

---

## Features

- **Real-Time Messaging**: WebSocket-based instant chat between users.
- **Timeline Posts**: Users can post updates that broadcast to followers.
- **Follow/Unfollow**: Live updates when users follow or unfollow others.
- **Background Polling**: Periodic sync for messages, posts, and follows to keep multiple servers consistent.
- **CORS Enabled**: Accepts connections from any frontend.
- **DB Service Integration**: Relies on an external DB microservice for persistence.

---

## Endpoints

### Chat & Posts
- `GET /chat/chats?username=<username>` – Fetch user’s chat history
- `GET /chat/timeline?currentUser=<username>` – Fetch timeline posts

### Follow System
- `POST /chat/follow?currentUser=<u>&targetUser=<t>` – Follow a user
- `POST /chat/unfollow?currentUser=<u>&targetUser=<t>` – Unfollow a user
- `GET /chat/users?currentUser=<u>` – List all users with follow status

### System
- `GET /chat/health` – Simple health check

---

## WebSocket

**Endpoint:**  
```/ws```

**Message Sending Prefix:**
```/app```

**Subscriptions:**
```
/topic/chat-<username>
/topic/timeline-<username>
/topic/follow-<username>
```

---

## Run

Start with Maven:

```bash
mvn "spring-boot:run" "-Dspring-boot.run.arguments=--server.port=9090"
mvn "spring-boot:run" "-Dspring-boot.run.arguments=--server.port=9091"
mvn "spring-boot:run" "-Dspring-boot.run.arguments=--server.port=9190"
mvn "spring-boot:run" "-Dspring-boot.run.arguments=--server.port=9191"
```

Build and run:
```
mvn clean package
java -jar target/chat-server.jar
```

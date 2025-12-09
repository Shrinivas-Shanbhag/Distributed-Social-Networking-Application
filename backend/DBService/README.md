# DB Service

A lightweight **Spring Boot database service** for the chat application.  
It persists **users’ followers, chats, and timeline posts** using JSON files.
(in future this service will be replaced by a distributed database service).

---

## Features

- **Followers Management**: Follow/unfollow users and track relationships.
- **Chat Persistence**: Store and retrieve direct messages between users.
- **Timeline Posts**: Store posts and retrieve timeline for a user, including posts from followed users.
- **Followers Lookup**: Get all users following a specific user.
- **Local JSON Storage**: Uses `followers.json`, `chats.json`, and `posts.json` for persistence.

---

## Endpoints

### Followers
- `POST /db/follow` – Follow/unfollow a user
- `GET /db/users?currentUser={username}` – List all users with `followed` flag
- `GET /db/followersOf?user={username}` – Get list of users following a specific user

### Chats
- `POST /db/chats` – Store a chat message
- `GET /db/chats?username={username}` – Retrieve all chats for a user

### Posts / Timeline
- `POST /db/posts` – Store a post
- `GET /db/timeline?currentUser={username}` – Get timeline posts for a user

---
## Run

Start the server with Maven:

```bash
mvn spring-boot:run
```

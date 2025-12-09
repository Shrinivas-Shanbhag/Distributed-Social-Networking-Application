# Distributed Social Network Frontend

A responsive **web client** for the Distributed Social Networking Application, enabling real-time interaction with posts and users.

---

## Overview

This frontend provides a **interface** where users can:

- Register and log in
- Follow or unfollow other users
- View posts from followed accounts in real-time
- Interact with a live timeline via WebSockets
- Chat with other users

It communicates with the backend services (Auth, Chat, DB) to provide a seamless and interactive experience.

---

## Features

- **User Authentication**: Login and registration with username/password
- **Follow/Unfollow Users**: Connect or disconnect with other users
- **Real-Time Timeline**: Live updates of posts from followed users
- **Responsive UI**: Works across desktop and mobile devices
- **WebSocket Integration**: Instant notifications for posts and chats

---

## Technology Stack

- **Frontend**: TypeScript, HTML, CSS
- **Communication**: WebSockets for real-time updates, REST for standard requests

---

## Run Locally

1. Install dependencies:
```bash
npm install
```
2. Start all the backend services
3. Start the development server:
```
npm run dev
```
4. Open in browser:
```
http://localhost:5173
```

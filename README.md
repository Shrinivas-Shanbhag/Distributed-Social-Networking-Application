# Distributed Social Networking App (LinkStream)

This project is a full-stack, distributed social networking platform. It provides real-time interaction, high availability, and fault-tolerant performance through a master–slave backend architecture and a responsive frontend interface.

---

## Overview

The system allows users to connect, share posts, and receive updates instantly. The backend operates on a distributed master–slave server setup with automatic failover, while the frontend offers a fast, responsive user experience. A built-in health monitor continually checks server status and promotes a slave server to master if a failure is detected.

---

## Key Features

- **User Authentication** – Secure username/password login.
- **Follow / Unfollow** – Users can easily connect to or disconnect from other accounts.
- **Real-Time Timeline** – Displays top or trending posts from followed users.
- **Distributed Backend** – Master–slave architecture for load sharing and fault tolerance.
- **Health Monitoring** – Detects master failure and automatically promotes a backup server.
- **Real-Time Updates** – WebSocket-based instant updates for posts and interactions.
- **Inter-Service Communication** – Efficient backend communication through web socket.

---

## Technology Stack

**Frontend**
- TypeScript, HTML, CSS (responsive web client)

**Backend**
- Java, Spring Boot
- Master–slave server architecture
- WebSockets for instant updates

---

## Potential Impact

- **High Availability** through automatic failover.
- **Real-Time Engagement** via WebSocket-driven updates.
- **Scalability** to support growing user load efficiently.
- **Resilience** with minimal downtime and reliable failover handling.
- **Low-Latency Performance** using optimized Java/Spring Boot backend and efficient protocols.

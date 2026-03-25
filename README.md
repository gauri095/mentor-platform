# MentorSpace – Real-time 1-on-1 Mentorship Platform

A full-stack **real-time mentorship platform** where mentors and students can join private sessions, video call, chat, and collaboratively edit code in a shared Monaco Editor.

Built exactly as per the official **Java Stack Roadmap** (Industry 9.5–10/10 level project).

## 🎯 Project Goal
Build a web-based 1-on-1 mentorship platform where a mentor and a student can:
- Join a private session
- Video call in real time
- Chat via messages
- Collaboratively edit code in a shared editor

## 🔥 Key Features
- ✅ Real-time collaborative code editor (Monaco Editor + STOMP WebSocket)
- ✅ 1-on-1 Video Call (WebRTC with Spring Boot signaling)
- ✅ Session-based real-time chat with message persistence
- ✅ JWT Authentication + Role-based access (MENTOR / STUDENT)
- ✅ Session management (create, join, end)
- ✅ Last-write-wins sync + frontend throttling
- ✅ Responsive UI with Tailwind CSS

## 🛠️ Tech Stack

### Frontend
- Next.js 15 (App Router) + TypeScript
- Tailwind CSS
- Monaco Editor
- `@stomp/stompjs` + SockJS (WebSocket client)

### Backend (Java)
- Spring Boot 3
- Spring Web + Spring Security (JWT)
- Spring WebSocket + STOMP
- Spring Data JPA + Hibernate
- PostgreSQL
- Lombok

### Real-time
- WebSocket (STOMP) for editor & chat
- WebRTC (frontend) with backend-only signaling



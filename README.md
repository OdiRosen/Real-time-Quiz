# Real-Time Quiz System

A Full-Stack, production-ready real-time quiz application built with a modern decoupled architecture. The system features a responsive, high-contrast gaming UI with robust state management, secure authentication, and low-latency bi-directional communication.

## 🚀 Live Demo
The application is fully deployed and available online:
* **Frontend Web App:** [https://real-time-quiz-frontend.onrender.com/](https://real-time-quiz-frontend.onrender.com/)
* **Backend API Service:** [https://real-time-quiz.onrender.com/](https://real-time-quiz.onrender.com/)
---

## 🛠️ Tech Stack & Architecture

The application is built using a decoupled client-server architecture, ensuring high scalability and separation of concerns.

### Backend
* **Framework:** Java / Spring Boot
* **Communication:** WebSocket (STOMP) & SockJS for real-time, bi-directional event-driven communication.
* **Database:** SQL/NoSQL [Specify your DB, e.g., PostgreSQL / MongoDB] for persistence.
* **Security:** Secure authentication flows (including Google OAuth2 integration).

### Frontend
* **Framework:** Angular (v21)
* **Styling:** Custom component-scoped CSS with advanced layout techniques and fluid responsiveness.
* **Routing:** Angular Router for secure, protected component routing.

---

## 💡 Key Features & Technical Implementation

* **Real-Time Synchronized State:** Utilizes WebSockets with STOMP protocol to sync game states, questions, and player actions with sub-millisecond latency.
* **Role-Based Access Control (RBAC):** Distinct interfaces and logic flows for **Admins** (quiz management and creation via Google Auth) and **Players** (anonymous or credential-based game entry).
* **Robust Type Safety:** Strict TypeScript implementation on the frontend and explicit Entity mapping on the backend to minimize runtime exceptions.
* **Modern UI Architecture:** Component-driven frontend layout using custom design tokens, optimal color-contrast handling, and fluid mobile responsiveness.

---

## ⚙️ Local Setup & Installation

### Prerequisites
* Node.js (v22+)
* Java JDK (v17+)
* Maven

### Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```


3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```



### Frontend Setup

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```


2. Install dependencies:
   ```bash
   npm install
   ```


3. Start the development server:
   ```bash
   ng serve
   ```


4. Open `http://localhost:4200` in your browser.

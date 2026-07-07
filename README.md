# SomeChat 💬

License: MIT

Platform: Android

Language: Java 100%

Backend: Firebase

**SomeChat** is a modern, feature-rich Android messaging application designed for fast, secure, and real-time communication. Built entirely in **Java** leveraging Android Jetpack, Room, WebRTC, and Firebase services, it features a beautiful, dynamic user interface inspired by Material 3 specifications.

<p align="center">
<img src="docs/img/logo.png" alt="SomeChat Logo" width="120" height="120">
</p>

## 🚀 Key Features

* **Real-time Syncing:** Instantly send and receive messages with typing states, delivery indicators, and read statuses powered by Firebase Realtime Database.
* **Peer-to-Peer Calls:** High-quality voice and video calls implemented via secure **WebRTC** protocols, incorporating STUN/TURN handling and dynamic audio routing.
* **Offline Caching:** Full-fledged local storage integration using **Room Database**. Browse your chats, contacts, and historical sessions even when offline.
* **Material You Integration:** Dynamic wallpaper color matching (Monet color engine support) that adapts the app's visual highlights directly to the user's system theme.
* **Secure Authentication:** Simple, unified access flows supporting secure Google Sign-In and robust Firebase Authentication rule gates.
* **Lightweight & Clean:** Optimized resource consumption with strictly monitored thread pipelines, ensuring sub-100ms database transactions.

## 🛠️ Technology Stack

* **Programming Language:** 100% Java
* **User Interface:** Jetpack Compose (utilizing Java interoperability layer) & Material 3 Components
* **Real-time Backend:** Firebase Realtime Database & Cloud Firestore
* **Storage & Caching:** Room SQLite Database
* **P2P Audio/Video:** WebRTC
* **Dependency Injection:** Dagger Hilt
* **Image Loading:** Coil / Glide
* **Build System:** Gradle (Kotlin DSL / Groovy)

## 🏗️ Architecture Design

SomeChat adheres closely to **Clean Architecture** principles and the **MVVM (Model-View-ViewModel)** design pattern. This ensures clear boundaries of concerns, high testability, and painless maintainability:

```text
 ┌────────────────────────────────────────────────────────┐
 │                      Presentation                      │
 │   [Jetpack Compose Views] ◄──► [ViewModels (State)]     │
 └───────────────────────────┬────────────────────────────┘
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │                      Domain Layer                      │
 │   [Use Cases / Interactors] ◄──► [Repository interfaces]│
 └───────────────────────────┬────────────────────────────┘
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │                       Data Layer                       │
 │  [Firebase SDK]  [Room SQLite DB]  [WebRTC Call Clients]│
 └────────────────────────────────────────────────────────┘

```

## 📦 Getting Started & Installation

### Prerequisites

* **Android Studio** (Ladybug or later recommended)
* **JDK 17** or higher
* Android SDK 26 (Android 8.0) or higher (Minimum SDK)
* An active **Firebase Project**

### 1. Clone the Repository

```bash
git clone https://github.com/Hamraj37/SameChat.git
cd SameChat

```

### 2. Configure Firebase Backend

1. Go to the Firebase Console.
2. Create a new project named SomeChat (or choose your preferred name).
3. Enable **Authentication** (Google & Email Sign-In).
4. Enable **Firestore Database** and **Realtime Database** under secure production rules.
5. Register your Android App using your package name (e.g., com.hamraj.somechat).
6. Download the google-services.json file and place it in the app/ directory of the cloned project:

```text
SomeChat/
├── app/
│   ├── google-services.json  <-- Place here
│   └── src/

```

### 3. Build & Run

Open the project in Android Studio, allow Gradle sync to complete, and deploy the app directly onto your physical testing device or emulator:

* Press **Run** (Shift + F10)

## 📁 Project Directory Overview

```text
├── app/
│   ├── src/main/
│   │   ├── java/com/hamraj/somechat/
│   │   │   ├── data/          # Databases (Room), API Call Clients, Repository Implementations
│   │   │   ├── domain/        # Domain entities, Repository Interfaces, Use Cases
│   │   │   ├── ui/            # UI screens, Compose components, ViewModels, Themes
│   │   │   └── utils/         # Network adapters, WebRTC helpers, cryptographic utilities
│   │   └── res/               # Vector icons, raw layouts, localization strings

```

## 🤝 Contributing

Contributions make the open-source community an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (git checkout -b feature/AmazingFeature)
3. Commit your Changes (git commit -m 'Add some AmazingFeature')
4. Push to the Branch (git push origin feature/AmazingFeature)
5. Open a Pull Request

## 📄 License

Distributed under the MIT License. See LICENSE for more information.

## 📞 Contact

* **Developer:** Hamraj37
* **Repository Link:** https://github.com/Hamraj37/SameChat

---

Made with ❤️ by Hamraj37

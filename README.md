<p align="center">
<img src="docs/img/logo.png" alt="Same Chat Logo" width="120" height="120" />
</p>
<h1 align="center">Same Chat</h1>
<p align="center">
<strong>A high-performance, lightweight, multi-threaded Java desktop chat application for real-time secure communication.</strong>
</p>
<p align="center">
<img src="https://img.shields.io/badge/Java-JDK_8%2B-007396?style=flat-for-the-badge&logo=openjdk&logoColor=white" alt="Java Version" />
<img src="https://img.shields.io/badge/Platform-Cross--Platform-emerald?style=flat-for-the-badge" alt="Platform Compatibility" />
<img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-for-the-badge" alt="License" />
</p>

Same Chat is a robust, lightweight Java-based messaging application architected around raw TCP/IP network sockets. Built entirely with core Java libraries, it runs cross-platform with zero external dependencies.

## ✨ Features

* ⚡ **Sub-millisecond Message Latency**: Powered by native Java byte stream buffers over raw TCP pipelines.
* 🧵 **Multi-Threaded Server Architecture**: Allocates isolated client-handler threads (ClientHandler) ensuring non-blocking operations for dozens of concurrent active loops.
* 👤 **Multi-User Sync Channel**: Robust thread synchronization parameters prevent race conditions and concurrent memory overlaps during massive message storms.
* 💾 **Local Log Storage**: Keeps a secure thread-safe audit history of messages written to local file paths automatically.
* 🔌 **Zero External Dependencies**: Needs no database servers, maven plugins, or heavy runtimes. Only a standard JVM (JDK 8+).

## 🛠 Technology Stack

* **Language**: Pure Java (JDK 8 or higher)
* **Networking**: java.net.ServerSocket, java.net.Socket
* **Concurrency**: Custom Thread Pool, Runnable, Multi-Threaded I/O Block pipelines
* **Persistence**: File System Buffers (java.io)

## 📦 Installation & Quickstart

### Prerequisites

To compile and run Same Chat, you need the **Java Development Kit (JDK) 8** or higher installed on your system and configured in your environment path.

To verify your installation, run:

```bash
java -version
```

### Build Steps

Follow these simple steps to compile and execute the application natively:

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/Hamraj37/SameChat.git
   ```

2. **Navigate to the Project Directory:**
   ```bash
   cd SameChat
   ```

3. **Compile the Java Source Files Natively:**
   ```bash
   javac -d bin src/**/*.java
   ```

4. **Run the Application:**
   ```bash
   java -cp bin MainClass
   ```

## 🚀 Usage Guide

1. **Start the Central Chat Server:** Ensure your network host port (Default: 8080) is open. Compile and run the core server class.
2. **Launch Client Instances:** Open separate terminals or execution instances representing different local clients on the network.
3. **Connect & Exchange Packets:** Register your username on the socket stream. Connect directly to localhost:8080 to send and receive messages in real-time.

## 📁 Project Structure

```text
SameChat/
├── src/                    # Java source files (*.java)
│   └── (Your Packages)
├── bin/                    # Compiled JVM bytecode (*.class)
├── docs/                   # Documentation resources
│   └── img/
│       └── logo.png        # Official branding resource
└── README.md               # This file
```

## ⚙️ Core Architecture Blueprint

Same Chat operates on a standard **Client-Server Architecture** utilizing custom thread allocations for maximum socket efficiency. Every incoming connection maps to an isolated ClientHandler thread, ensuring efficient resource management and scalability.

## 🤝 Contributing

Contributions make the open-source community an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (git checkout -b feature/AmazingFeature)
3. Commit your Changes (git commit -m 'Add some AmazingFeature')
4. Push to the Branch (git push origin feature/AmazingFeature)
5. Open a Pull Request

## 📄 License

This project is open-source and released under the MIT License.

**Questions, feedback, or suggestions?** Feel free to open an issue or submit a pull request on the GitHub repository!

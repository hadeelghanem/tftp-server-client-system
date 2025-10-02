# TFTP Server & Client

This repository contains my solution for **Assignment 3** of the Systems Programming Laboratory course.  
The goal of the project was to implement a **TFTP (Trivial File Transfer Protocol)** server and client in **Java**, with support for file transfers, directory queries, and broadcast notifications.

---

## Overview
- Implemented a **multi-threaded server** that:
  - Handles multiple clients concurrently (Thread-Per-Client).  
  - Supports login, file upload/download, delete requests, and directory queries.  
  - Sends broadcast messages when files are added or removed.  

- Implemented a **multi-threaded client** that:
  - Uses separate threads for keyboard input and server responses.  
  - Sends requests such as `LOGRQ`, `RRQ`, `WRQ`, `DIRQ`, `DELRQ`, and `DISC`.  
  - Receives `DATA`, `ACK`, `BCAST`, and `ERROR` packets.  

---

## Features
- **File Transfer** – upload and download files in 512-byte chunks.  
- **Directory Listing** – list all available server files.  
- **Delete Requests** – remove files from the server.  
- **Broadcast Updates** – all clients receive notifications of file changes.  
- **Error Handling** – invalid requests and edge cases handled gracefully.  
- **Maven Build** – modular project with separate server and client builds.  

---

## Skills Gained
- **Java socket programming** and concurrency.  
- Implementing custom **binary communication protocols**.  
- Designing **multi-threaded client/server systems**.  
- Managing synchronization and fairness across multiple clients.  
- Using **Maven** for project structure and execution.  

---

## ▶️ How to Run

```bash
Start the server:
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="7777"

Start the client:
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="127.0.0.1 7777"

# Spring Boot WebClient - Reactive HTTP Client Example

A modern Spring Boot application demonstrating best practices for building reactive HTTP clients using Spring WebFlux and R2DBC. This project integrates external API calls with PostgreSQL database persistence using reactive programming.

## 🎯 Project Overview

This project showcases a production-grade implementation of: 
- **Reactive HTTP Client** using Spring WebFlux's `WebClient`
- **Reactive Database Access** using R2DBC with PostgreSQL
- **Advanced Error Handling** with custom exception handling and retry logic
- **Request/Response Logging** and monitoring
- **Connection Management** with timeouts and connection pooling

## ✨ Key Features

- **Asynchronous HTTP Communication**: Non-blocking HTTP requests using Spring WebFlux
- **Exponential Backoff Retry Strategy**: Automatic retry mechanism with configurable backoff
- **Comprehensive Error Handling**:  Handles 4xx/5xx errors, timeouts, and connection failures
- **Request/Response Filtering**: Logging filters and request validation
- **R2DBC Integration**: Reactive database access with PostgreSQL
- **Upsert Operations**: Efficient conflict resolution for duplicate entries
- **Request Timeouts**: Configurable connection, read, and write timeouts
- **Custom Headers**: Request tracing with X-Request-ID headers

## 🛠 Technology Stack

| Component | Version |
|-----------|---------|
| **Java** | 25 |
| **Spring Boot** | 4.0.1 |
| **Spring WebFlux** | Latest |
| **Spring Data R2DBC** | Latest |
| **PostgreSQL Driver** | R2DBC Compatible |
| **Project Lombok** | Latest |
| **Apache Commons Lang** | Latest |
| **Reactor** | Latest |
| **Netty** | Latest |

## 📋 Prerequisites

- Java 25 or higher
- Maven 3.6+
- PostgreSQL 12 or higher
- Git

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/priyodas12/springboot-webclient.git
cd springboot-webclient

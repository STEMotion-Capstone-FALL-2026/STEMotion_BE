# STEMotion - Project Requirements & Master Context

## 1. Project Overview
- **English Name:** STEMotion - AI-Assisted STEM Video Production Platform Using Remotion.
- **Vietnamese Name:** STEMotion - Nền Tảng Sản Xuất Clip STEM Có Hỗ Trợ AI Trên Nền Remotion[cite: 29].
- **Context & Problem:** Producing short Vietnamese STEM explainer clips is slow and manual. STEMotion wraps the full lifecycle into one platform with human-in-the-loop AI[cite: 29].

## 2. State Machine & Content Lifecycle
The platform manages content lifecycle via a role-based state machine:
`Draft` ➔ `Script Review` ➔ `Approved` ➔ `In Production` ➔ `Clip Review` ➔ `Published`[cite: 29].

## 3. Functional Requirements by Roles & Modules
- **Script Studio (Writer):** Create projects (Math, Physics, Chemistry, Biology, CS; target grades), write scene-by-scene with version history, request AI assistance, submit for review[cite: 29].
- **Review & Approval (Reviewer/Editor):** Review scripts with inline per-scene comments, review rendered clips with timestamped annotations, compare versions across iterations[cite: 29].
- **Production Workspace (Producer):** Open approved scripts as auto-generated drafts mapped to Remotion templates (Title, Formula/step, Diagram, Data chart, Algorithm, Quiz, Outro), edit properties, preview in Remotion Player, tune narration/captions, submit render jobs[cite: 29].
- **AI Assistance (Human-in-the-Loop):** Script-to-scene segmentation (LangChain + GPT-4o-mini), concept extraction, readability scoring, Vietnamese TTS & Whisper caption alignment, automated pre-publish QA[cite: 29].
- **Publishing, Library & Admin:** One-click YouTube Data API publishing, internal media library with filters, user/role management (RBAC), template library, and system monitoring[cite: 29].

## 4. Technical Architecture & Tech Stack
- **Main API (Backend):** Spring Boot (Java 17)[cite: 29].
- **Rendering Service:** Node.js + Remotion, BullMQ + Redis queue, headless Chromium worker pool[cite: 29].
- **AI Microservice:** Python FastAPI + LangChain, GPT-4o-mini, Whisper for captions, FPT.AI / Google Cloud TTS[cite: 29].
- **Database & Storage:** PostgreSQL (projects, versions, states, metadata) + Redis + MinIO/AWS S3[cite: 29].

## 5. Implementation Roadmap (7 Packages)
- **Package 1 (Week 1-2):** Foundation (Repo, PostgreSQL schema, JWT + Google OAuth 2.0, RBAC, Docker, CI/CD)[cite: 29].
- **Package 2 (Week 3-4):** Script & Workflow Core (Script Studio, versioning, state machine, inline comments, pipeline board)[cite: 29].
- **Package 3 (Week 5-7):** AI Script Intelligence (FastAPI integration, segmentation, analysis, TTS, auto-captioning)[cite: 29].
- **Package 4 (Week 8-9):** Remotion Production Pipeline (Template mapping, scene editor, BullMQ render queue & workers)[cite: 29].
- **Package 5 (Week 10-11):** Clip Review, QA & Publishing (Timestamped review, automated QA, YouTube publishing, media library)[cite: 29].
- **Package 6 (Week 12-13):** Admin, Integration & Testing (Admin panel, template management, cost/usage monitoring, E2E testing)[cite: 29].
- **Package 7 (Week 14):** Deployment & Documentation (Production deployment, producing 10+ real clips, final docs & presentation)[cite: 29].
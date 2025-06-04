# Habit Tracker

**Habit Tracker** is an Android mobile application designed to help users build positive habits and overcome addictions through personalized tracking, customizable notifications, and community support. Developed as part of a diploma project, the app leverages Firebase Realtime Database for real-time data synchronization and Google Sign-In for secure authentication. Key features include habit creation, progress tracking with statistical charts, goal setting, daily review notifications, accountability partner requests, and real-time chats.

## Table of Contents
- [Features](#features)
- [Screenshots](#screenshots)
- [Installation](#installation)
- [Usage](#usage)
- [Project Setup](#project-setup)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

## Features
- **Habit Creation and Tracking**:
  - Add and manage habits (e.g., "Reading") or addictions (e.g., "Cigarettes").
  - Track progress with streaks, percentage-based metrics, and completion history.
  - Mark daily completions or relapses for habits/addictions.

- **Statistics with Charts**:
  - View detailed statistics for each habit and addiction, including progress over time.
  - Visual charts (e.g., line or bar graphs) display streaks, completion rates, and relapse trends.
  - Accessible via a dedicated stats screen for each habit/addiction.

- **Goal Setting**:
  - Create specific goals for habits or addictions (e.g., "Read 30 days in a row" or "90 days without smoking").
  - Track goal progress with visual indicators and achievement notifications.
  - Goals are stored in Firebase and synced with local storage.

- **Customizable Notifications**:
  - Set daily, hourly, or custom-interval reminders for habits and goals.
  - **Daily Review Notifications**: Receive a notification to remind of marking victories for addictions.
  - Notifications respect user-defined sleep schedules to avoid disturbances.

- **Accountability Partners**:
  - Submit requests to find support partners for specific habits/addictions.
  - View and accept requests from other users.
  - Real-time updates for request status via Firebase.

- **Real-Time Chats**:
  - Group chats or private accountability chats to stay motivated.
  - Dynamic chat list updates instantly when new chats are added.
  - Supports global and user-specific chats with access restrictions.

- **Secure Authentication**:
  - Google Sign-In with account selection prompt for flexible login.
  - Secure logout clears all local data (SharedPreferences), notifications, and supports switching Google accounts.
  - Offline support with local storage and Firebase synchronization.

- **Firebase Integration**:
  - Real-time database for habits, stats, goals, chats, and user data.
  - Offline caching via SharedPreferences for seamless operation.

## Screenshots
*Coming soon! Screenshots of the app’s UI, including habit tracking, statistics, goal setting, chat interface, and settings, will be added in the next release.*

*Placeholder:*
- Habit List: `screenshots/habit_list.png`
- Statistics Screen: `screenshots/stats_screen.png`
- Goal Creation: `screenshots/goal_creation.png`
- Chat Interface: `screenshots/chat_screen.png`
- Settings Screen: `screenshots/settings.png`

## Installation
1. **Download the APK**:
   - Visit the [Releases](https://github.com/AlexPechkin2004/habit-tracker/releases) page and download the latest APK.
   
2. **Install on Android**:
   - Enable "Install from Unknown Sources" in your device settings (Settings > Security).
   - Open the downloaded APK and follow the installation prompts.

3. **Sign In**:
   - Launch the app and sign in with a Google account to access all features.
   - Ensure an active internet connection for initial Firebase synchronization.

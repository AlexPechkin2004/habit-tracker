# Habit Tracker

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/AlexPechkin2004/habit-tracker/actions)

**Habit Tracker** is an Android mobile application designed to help users build positive habits and overcome addictions through personalized tracking, customizable notifications, and community support. Developed as part of a diploma project, the app leverages Firebase Realtime Database for real-time data synchronization and Google Sign-In for secure authentication. Key features include habit creation, progress tracking, accountability partner requests, real-time chats, and a secure logout mechanism.

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
  - Track progress with streaks and percentage-based metrics.
  - Mark daily completions or relapses for habits/addictions.

- **Customizable Notifications**:
  - Set daily, hourly, or custom-interval reminders for habits.
  - Notifications respect user-defined sleep schedules to avoid disturbances.
  - All notifications are canceled upon logout for privacy.

- **Accountability Partners**:
  - Submit requests to find support partners for specific habits/addictions.
  - View and accept requests from other users, filtered by age and gender.
  - Real-time updates for request status via Firebase.

- **Real-Time Chats**:
  - Create group chats or accountability chats to stay motivated.
  - Dynamic chat list updates instantly when new chats are added.
  - Supports global and user-specific chats with access restrictions.

- **Secure Authentication**:
  - Google Sign-In with account selection prompt for flexible login.
  - Secure logout clears all local data (SharedPreferences) and notifications.
  - Offline support with local storage and Firebase synchronization.

- **Firebase Integration**:
  - Real-time database for habits, stats, chats, and user data.
  - Offline caching via SharedPreferences for seamless operation.

## Screenshots
*Coming soon! Screenshots of the app’s UI, including habit tracking, chat interface, and settings, will be added in the next release.*

*Placeholder:*
- Habit List: `screenshots/habit_list.png`
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

## Usage
1. **Create a Habit**:
   - Navigate to the "Habits" tab and tap the floating action button (+).
   - Select a predefined habit (e.g., "Reading") or add a custom one.
   - Set a reminder schedule (e.g., daily at 18:00).

2. **Track Progress**:
   - Mark daily completions in the habit’s card to update streaks and progress.
   - For addictions, mark victories or relapses to track sobriety.

3. **Find Accountability Partners**:
   - Go to the "Messages" tab, select "Accountability Partners," and create a request.
   - Specify the habit and your preferences (e.g., age, gender).
   - Accept incoming requests to start a private accountability chat.

4. **Chat with Others**:
   - Access the "Chats" tab to view or create group chats.
   - Join global chats or accountability chats to share progress and motivation.

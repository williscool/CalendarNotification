# Calendar Notifications Plus Data Sync Setup Guide

This guide will help you set up data synchronization for Calendar Notifications Plus using Supabase and PowerSync.

## Prerequisites

- A Supabase account
- A PowerSync account
- Access to the Calendar Notifications Plus app

## Setup Steps

### 1. Supabase Setup

1. Sign up for Supabase and create a new project at [https://supabase.com](https://supabase.com)
2. Once your project is created, navigate to the project dashboard
3. Make note of your project URL and anon key (found in Project Settings > API)
4. Migrate the database schema:
   ```bash
   # From the root of this project
   cd supabase
   supabase link --project-ref <your-project-id>
   supabase db push
   ```
   This will create the required `eventsV9` table needed for event synchronization.

### 2. PowerSync Setup

1. Sign up for PowerSync at [https://powersync.com](https://powersync.com)
2. Create a new instance
3. Connect PowerSync to your Supabase database:
   - Follow the connection instructions in the PowerSync dashboard
   - Make sure to configure the necessary permissions and access controls
4. Configure HS256 authentication:
   - Go to your instance settings → Client Auth tab
   - Under **"JWT Audience (optional)"**, click the "+" button and add: `powersync`
   - Under **"HS256 authentication tokens (ADVANCED)"**, click the "+" button
   - For **KID**, enter: `powersync`
   - For **HS256 secret**, generate one: `openssl rand -base64url 32`
   - Copy the generated secret (you'll need it for the app)
   - Click **"Save and deploy"**

### 3. Calendar Notifications Plus Configuration

1. Open the Calendar Notifications Plus app
2. Navigate to Data Sync Settings
3. Configure the following:
   - Supabase URL (from step 1.3)
   - Supabase Anon Key (from step 1.3)
   - PowerSync Instance URL (from step 2.2)
   - PowerSync Token (paste the same HS256 secret from step 2.4)

> **Note:** The app automatically generates short-lived JWT tokens (5 min expiry) using this secret. The JWTs include `sub` (device ID), `aud` (powersync), `iat`, and `exp` claims. Unlike development tokens, the HS256 secret doesn't expire - you only need to configure it once.

## Verification

1. After configuring all settings, the app should show "PowerSync Status: Connected"
2. Try creating a new calendar event - it should sync to the remote database
3. If using multiple devices, changes should propagate between them

## Troubleshooting

- If sync isn't working, verify all credentials are entered correctly
- Check the PowerSync status in the app
- Ensure your Supabase and PowerSync instances are running and connected
- Review the app logs for any error messages
- **JWT errors:** Make sure the HS256 secret in the app matches exactly what's configured in PowerSync dashboard
- **"Invalid token" errors:** Verify the HS256 secret is properly configured under Client Auth → HS256 authentication tokens
- **"Missing aud claim" errors:** Add `powersync` to JWT Audience in the PowerSync dashboard (Client Auth → JWT Audience)
- **"Unexpected aud claim" errors:** The JWT Audience in PowerSync dashboard must include `powersync`

## Additional Resources

- [Supabase Documentation](https://supabase.com/docs)
- [PowerSync Documentation](https://docs.powersync.com)
- [PowerSync + Supabase Integration Guide](https://docs.powersync.com/integration-guides/supabase-+-powersync)
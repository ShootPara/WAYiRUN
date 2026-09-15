# WAYiRUN — Google sign-in setup

## 1 What to create now

Create a development Google Cloud project and a Web OAuth client. These provide the identity configuration for WAYiRUN; run storage stays in Cloudflare. Google project creation does not connect the installed APK by itself.

## 2 Google Cloud project

Open [Google Cloud Console](https://console.cloud.google.com/). Use the project picker to create a project named **WAYiRUN Development**, then select it. Keep the generated project ID; its globally unique value may differ from the display name.

Open **Google Auth Platform**. If it shows **Get started**, complete that setup. Use **WAYiRUN** as the app name and your own support/contact email. Choose **External** for an ordinary personal Google account. Keep the project in **Testing** and add the Google account you use on the phone under **Audience → Test users**. Do not publish the application as part of this setup. These sections are described in [Google Auth Platform help](https://support.google.com/cloud/answer/15544987).

Use only basic sign-in scopes (`openid`, `email`, `profile`); no Drive, YouTube, fitness, or other Google API access is needed. Leave optional logo/domain/website fields for the later web application; do not invent a privacy-policy URL. Google's [sign-in setup documentation](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid) describes branding and basic scopes.

## 3 Web client

In **Google Auth Platform → Clients**, choose **Create client**:

| Field | Value |
| --- | --- |
| Application type | Web application |
| Name | WAYiRUN development backend |
| Authorized JavaScript origins | Leave empty for this Android backend-audience setup |
| Authorized redirect URIs | Leave empty; this backend does not implement an authorization-code redirect endpoint |

Create the client and copy its **Client ID**, ending in `.apps.googleusercontent.com`. This identifier is safe to give me. Do not send the client secret or downloaded credentials JSON. The implemented ID-token verification flow does not use a client secret. [Google backend verification](https://developers.google.com/identity/sign-in/android/backend-auth).

Send me these two values: the **project ID** and the **Web client ID**. There is no need to change Cloudflare bindings yourself.

## 4 What I will connect next

I will configure the verified audience, then wire Android Credential Manager to obtain a Google ID token using a fresh server challenge. The Worker exchanges that verified identity for an expiring WAYiRUN session. Private account responses derive ownership from that session.

Android also needs an Android OAuth client in the same Google project with the exact application ID and signing-certificate SHA-1. We have not finalized that identity. Do not create an Android client from the current `com.example.runningapp` prototype. I will supply exact values after checking the app-identity and signing plan; changing identity must preserve access to existing test runs.

Before enabling real sign-in, the remaining integration checks include approved Android authorized-party IDs, request-rate limits for public login endpoints, the actual phone sign-in flow, and returning-user offline behavior. No settings change here enables run synchronization or assigns old local runs to a Google account.

## 5 Current verification boundary

The account backend has local tests using generated test signatures and an intercepted Google key response. Deployed code always fetches Google's fixed public key endpoint; it has no test-login switch. Until the real audience is supplied, account/authentication endpoints return an unavailable response and create no accounts or login challenges.

The installed Android app remains local-only. Continue using its existing run features while this account integration is completed.

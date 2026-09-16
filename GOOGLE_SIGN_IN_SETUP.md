# WAYiRUN — Google sign-in setup

## 1 What to create now

Setup is complete for development: the user supplied project `wayirun-development`, its Web client, and its Android phone-testing client on September 15. The steps below are retained for reference. Run storage stays in Cloudflare; no client secret is required.

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

The development-only Android client now uses the existing installed package and verified debug signing certificate, so the app can update in place and preserve local runs. This does not register or finalize the production application identity.

| Development configuration | Value |
| --- | --- |
| Google project | wayirun-development |
| Web client | 933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com |
| Android client | 933230558080-8o82hopmd4ibnt2fllqpr8252lg3q44t.apps.googleusercontent.com |
| Android package | com.example.runningapp.debug |
| Debug SHA-1 | E8:29:5C:0F:4A:15:A5:7D:87:38:CE:28:6F:67:87:6C:91:CF:05:94 |

The backend accepts that exact Web audience and allows that Android client as an authorized party. Login request-rate controls are implemented. Actual Google phone sign-in must still be verified; no configuration change enables run synchronization or assigns old local runs to a Google account.

## 5 Current verification boundary

The account backend has local tests using generated test signatures and an intercepted Google key response. Deployed code always fetches Google's fixed public key endpoint; it has no test-login switch. A valid Google token with a fresh server nonce is required to create an account/session.

The signin1 test APK adds optional Google sign-in and encrypted session storage. Run tracking/storage remains local-only. Existing runs keep their original local owner. Sign-in failure, cancellation, or expired sessions never gate START RUNNING. Completed-run synchronization remains the next Milestone 5 slice.


## 6 Phone verification - September 15, 2026

The user confirmed that Google sign-in succeeded on the installed phone build. Google setup is no longer awaiting that check. Run synchronization remains separate, unfinished work.

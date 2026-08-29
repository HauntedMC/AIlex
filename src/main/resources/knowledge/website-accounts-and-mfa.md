---
id: hauntedmc.website-accounts-mfa
title: HauntedMC accounts, HauntyLink, website MFA and in-game 2FA
aliases: [account, login, register, profile, password reset, email verification, mfa, 2fa, hauntylink, /link, discord link, beveiliging]
category: account-help
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: current HauntedMC WebApp account help + operator-provided HauntyLink/2FA help + 2026-08-04 linking update
---

## Website accounts
The current website supports registration/login, email verification, password reset, authenticated password changes, profile editing, avatars and email-based multi-factor authentication.

## Website email MFA
Website MFA uses a short-lived one-use verification code sent to the account email address. Enable it under **Account > Security** and confirm with the emailed code. A meaningful request-context change such as an IP change can invalidate a session and require verification again.

If an unexpected code arrives, ignore it and change the password. Never ask a player to post an email MFA code or password in chat.

## In-game authenticator 2FA
In-game `/2fa` is a separate security system and is still listed in current global command documentation. In the hub, `/2fa` provides a one-time QR-code map that is scanned with Google Authenticator or another authenticator app. The generated code is then verified with `/2fa <code>`.

The documented in-game authentication session requires a new code after an IP change or after 30 days. A lost phone/tablet is handled through Support so 2FA can be reset and linked again.

Website email MFA and in-game authenticator 2FA must not be described as the same mechanism.

## HauntyLink: Minecraft account
HauntyLink connects Minecraft with HauntedMC services. Linking is optional, but it is used for synchronizing account identity/rank and access to linked community services.

The documented Minecraft linking flow is: log in on the website, join Minecraft, run `/link`, then follow the generated official link. The August 2026 update states that link/synchronization requests now require a secure connection, duplicate requests are prevented and simultaneous syncs for one player are combined.

Do not reconstruct a linking URL or ask for a website password in game chat; let the official generated flow handle authentication.

## Discord linking
HauntedMC also supports linking a Discord account to the HauntedMC account through the official website/Discord authorization flow. Linked ranks are synchronized across services where configured. Use the current official link page or `/discord` rather than inventing authorization instructions if the web flow changes.

## Recovery
Password/account recovery, lost-authenticator resets and failed linking that cannot be resolved through the normal flow belong with `https://hauntedmc.nl/support`.

---
id: hauntedmc.website-accounts-mfa
title: Accounts, HauntyLink, website MFA and in-game 2FA
aliases: [account, login, register, profile, password reset, email verification, mfa, 2fa, hauntylink, /link, discord link, beveiliging]
category: account-help
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: current HauntedMC WebApp help + operator-provided HauntyLink/2FA docs + 2026-08-04 linking update
---

## Website account/MFA
Website supports registration/login, email verification, password reset/change, profile/avatar editing and email MFA. Website MFA sends a short-lived one-use code; enable under **Account > Security**. IP/request-context changes can require verification again. Unexpected code: ignore and change password. Never request passwords or codes in chat.

## In-game 2FA
Separate system: `/2fa` in hub gives a one-time QR-code map for an authenticator app; `/2fa <code>` verifies. Documented re-authentication: IP change or 30 days. Lost device: Support reset. Website email MFA and in-game authenticator 2FA are not interchangeable.

## HauntyLink
Minecraft link: log into website → join Minecraft → `/link` → follow the generated official link. Linking is optional but synchronizes identity/rank/access across configured services. August 2026: secure connection required; duplicate requests prevented; simultaneous syncs for one player combined. Never reconstruct the link or ask for website credentials in chat.

Discord can be linked through the official website/Discord authorization flow; configured ranks synchronize. Use current official link page or `/discord` if the web flow changes.

Recovery for passwords, lost authenticators or unresolved linking: `https://hauntedmc.nl/support`.

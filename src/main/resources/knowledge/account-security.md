---
id: hauntedmc.account-security
title: Account security and two-factor authentication
aliases: [account security, 2fa, mfa, authenticator, password reset, beveiliging]
category: account-help
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/twofactor.md | https://www.hauntedmc.nl/help/2fa/
---

In-game two-factor authentication protects eligible accounts with an authenticator app. Use `/2fa` to view your current status. When setup is required and available to your account, use `/2fa setup`; it provides the TOTP details to add manually in your authenticator app. Then enter the displayed numeric code with `/2fa <code>`.

Successful verification can return you to the server you were trying to join. Trusted-login duration and IP matching are live security settings, so do not rely on a fixed re-authentication interval. If you lose your device, request a reset through support. Never share your password, 2FA secret, or authenticator code.

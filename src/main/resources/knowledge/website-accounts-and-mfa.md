---
id: hauntedmc.website-accounts-mfa
title: HauntedMC website accounts, account access and email MFA
aliases: [account, login, register, profile, password reset, email verification, mfa, 2fa, two factor, beveiliging, trusted device]
category: account-help
authority: official
updated: 2026-08-29
expires: null
source: https://hauntedmc.nl/help/account/two-factor-authentication + current HauntedMC WebApp public account surfaces
---

## Website account capabilities
HauntedMC's current website account system supports registration and login, email verification, password reset, authenticated password changes, profile editing, avatars and email-based multi-factor authentication. The help center is the correct source for the current account flow; some migrated account help pages are still placeholders and should not be treated as detailed instructions.

## Email-based MFA
For website accounts, MFA works by logging in with the password and then entering a short-lived verification code sent to the account email address. Codes expire and can be used once. A meaningful request-context change, such as an IP change, can invalidate a session and require verification again.

Enable email MFA through **Account > Security** and confirm the setting with the code sent to the account email. Some account roles can require MFA.

Keep access to the account mailbox. If a code expires, request a new one instead of reusing an old email. If a player receives a code they did not request, they should ignore the code and change their password.

## Do not confuse website MFA with old in-game 2FA documentation
Older HauntedMC help material referenced an in-game `/2fa` flow. The newest reviewed public account documentation describes **email-based website MFA**. Do not tell a player that `/2fa` is the current website-MFA setup command unless current in-game help separately verifies that legacy feature.

## Account recovery and secrets
Use official password-reset/account-security flows and `/support` when recovery needs staff. Haunty must never ask a player to reveal a password, password-reset token, MFA code, email verification code or other authentication secret in chat.
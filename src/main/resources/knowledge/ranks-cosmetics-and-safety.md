---
id: hauntedmc.ranks-cosmetics
title: Current rank tags, cosmetics and perk safety
aliases: [rank, ranks, gast, speler, elite, legend, supreme, supreme+, cosmetics, pets, disguises, balloons, glow, particles, fragments, refer, 2fa]
category: server-feature
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-provided HauntedMC public rank/cosmetics/2FA documentation 2026-08-29
---

## Current rank tags and progression
Current public documentation contains these rank tags/statuses: `Gast`, `Speler`, `Elite`, `Legend`, `Supreme` and `Supreme+`.

`Gast` is a basic guest tag/status rather than a purchasable progression tier. The normal public player progression is `Speler` → `Elite` → `Legend` → `Supreme` → `Supreme+`. Do not resurrect historical rank/product names such as `God` as a current tier.

## Cosmetics
Cosmetic families are Disguises, Pets, Particle Effects/Styles, Balloons and Glow Effects. Use `/cosmetics` as the main selector; documented related routes include `/pets`, `/disguises`, `/balloons`, `/glow` and `/pp`.

The public cosmetics help documents four crate tiers: Tier 1 through Tier 4. Keys can be obtained with Cosmetic Fragments, while cosmetic keys may also be sold through the official Store.

## Cosmetic Fragments and referrals
The current operator-provided public help documents these fragment rewards:
- voting: 10 Cosmetic Fragments;
- online time: 25 Cosmetic Fragments per hour;
- successful referral: 200 fragments to the referring player and 400 to the new player.

The referral flow uses `/refer`. The same help snapshot documents 313 cosmetics in total and 49,150 fragments as the combined key cost across the documented cosmetic collection. If the live cosmetics menu has changed, the live menu wins over these counts.

## Rank perks
Rank-dependent systems include `/skin`, `/nickname`, full-server access, `/ping`, poses, `/fly`, `/god`, AutoPickup, home/claim/plot/shop capacity, backpacks and WorldEdit limits. Exact current quantities are in the dedicated rank-features knowledge and `/ranks` remains the strongest live player-facing source.

## In-game 2FA is separate from website MFA
The current global command documentation still lists `/2fa` and `/2fa <code>`. In-game 2FA uses an authenticator app and is configured in the hub: `/2fa` provides a one-time QR-code map to scan, then `/2fa <code>` verifies the authenticator code.

The documented in-game session requires a fresh code after an IP change or after 30 days. Lost-device resets go through Support. Never ask a player to reveal a live authenticator code in normal chat.

Website email MFA is a separate website-account security mechanism. Do not treat the two systems as interchangeable.

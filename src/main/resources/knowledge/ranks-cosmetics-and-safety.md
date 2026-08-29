---
id: hauntedmc.ranks-cosmetics
title: Rank tags, cosmetics and in-game 2FA
aliases: [rank, ranks, gast, speler, elite, legend, supreme, supreme+, cosmetics, pets, disguises, balloons, glow, particles, fragments, refer, 2fa]
category: server-feature
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-provided HauntedMC rank/cosmetics/2FA documentation 2026-08-29
---

## Ranks
Current tags/statuses: `Gast`, `Speler`, `Elite`, `Legend`, `Supreme`, `Supreme+`. `Gast` is a guest/basic status, not a purchasable progression tier. Progression: **Speler → Elite → Legend → Supreme → Supreme+**. Historical names such as `God` are not current ranks.

## Cosmetics
Families: Disguises, Pets, Particle Effects/Styles, Balloons, Glow Effects. Main route `/cosmetics`; related `/pets`, `/disguises`, `/balloons`, `/glow`, `/pp`. Four crate tiers (1–4); keys can use Cosmetic Fragments and may also be sold in the Store.

| Fragment source | Reward |
|---|---:|
| Vote | 10 |
| Online | 25/hour |
| Successful `/refer` | referrer 200; new player 400 |

Public help snapshot: 313 cosmetics; 49,150 fragments combined documented key cost. Live cosmetics menu overrides these counts if changed.

## In-game 2FA
Current global docs list `/2fa` and `/2fa <code>`. In the hub, `/2fa` gives a one-time QR-code map for Google Authenticator or another authenticator; `/2fa <code>` verifies it. Re-authentication is documented after IP change or 30 days. Lost device: Support reset. Never request a live authenticator code in chat.

Website email MFA is separate; do not conflate the systems.

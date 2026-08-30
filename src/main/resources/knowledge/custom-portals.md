---
id: hauntedmc.custom-portals
title: Custom portals
aliases: [custom portal, portal teleport, portal server transfer]
category: navigation
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/portals.md
---

Walk into a configured custom portal to trigger its action. A portal can teleport you locally, run its configured action, or request a move to another server. Anyone can use a configured portal unless a normal protection system prevents reaching it.

Portal actions have a short cooldown after a successful use. If a custom portal fails to act, it should leave normal vanilla Nether or End travel alone rather than trapping you in a failed transition.

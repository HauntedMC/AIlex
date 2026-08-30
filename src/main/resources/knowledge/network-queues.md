---
id: hauntedmc.network-queues
title: Restarts, reconnecting, and queues
aliases: [restart, autoreconnect, /autoreconnect cancel, queue, /queue leave, maintenance]
category: network
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/queue.md | https://www.hauntedmc.nl/threads/grote-hauntedmc-update.16064/
---

When a supported game mode is full, HauntedMC can put you in its queue and connect you when capacity becomes available. Use `/queue` or `/q` to view your queue status and `/queue leave` to leave it.

A queue is created only for a normal connection denied because the destination is full. Maintenance, restarts, security checks, unavailable targets, and other connection failures are not queues. You can have one active queue; joining another eligible full target moves you there. If you disconnect briefly, your position can be retained for a limited grace period. Position and wait time are live values, so do not treat them as a promise.

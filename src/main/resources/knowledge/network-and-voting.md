---
id: hauntedmc.network-voting
title: Network restarts, reconnects, queues, scheduled events and voting
aliases: [restart, autoreconnect, /autoreconnect cancel, queue, /queue leave, vote, voting, partymine, scheduler]
category: current-operations
authority: official
updated: 2026-08-26
expires: null
source: https://www.hauntedmc.nl/threads/grote-hauntedmc-update.16064/
---

## Restarts and automatic reconnect
When a game mode restarts, players can be moved to a lobby, limbo or fallback server while HauntedMC attempts to reconnect them after the target is available again. `/autoreconnect cancel` opts out of that automatic reconnect flow.

Do not estimate restart duration. Maintenance and readiness are live operational facts.

## Queues
If a target game mode is full and a queue is active, HauntedMC can connect queued players when a place becomes available. `/queue leave` leaves the queue. Queue position, priority and wait time are live state; do not guess them.

## Voting while a target is unavailable
The August 2026 network update documents votes received while a target game mode is unavailable being queued for later delivery for up to 24 hours. If a reward still has not arrived after the applicable delivery window, direct the player to support rather than promising manual compensation.

## Scheduled events
HauntedMC has a Command Scheduler for fixed and randomized automatic events. The August 2026 update mentioned Partymine on Monday at 20:00. That schedule is time-sensitive and must not be treated as permanent: use a current in-game schedule/announcement when asked when the next Partymine occurs.

## Voting
Use `/vote` for the current vote routes and reward information. HauntedMC publishes monthly top-voter information. Vote sites, rewards, streaks, reset time and top-voter prizes can change; current `/vote` or official announcements are the source of truth.
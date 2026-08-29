---
id: hauntedmc.network-voting
title: Network restarts, reconnects, queues, scheduled events and voting
aliases: [restart, autoreconnect, /autoreconnect cancel, queue, /queue leave, vote, voting, /vote top, /vote stats, partymine, scheduler]
category: current-operations
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: HauntedMC vote changelog 2026-03-06 + major network update 2026-08-04 + operator-provided public vote help
---

## Restarts and automatic reconnect
When a game mode restarts, players can be moved to a lobby, limbo or fallback server while HauntedMC attempts to reconnect them after the target is ready again. `/autoreconnect cancel` opts out. Players are returned gradually rather than all at once, and a manual server switch/disconnect should prevent an unwanted automatic return.

## Queues
When a game mode is full, HauntedMC can queue players against the centrally managed real capacity. `/queue leave` leaves the queue. The system considers server status, reserved capacity, maintenance/restarts, connection failures and player ordering/priority. Queue position and wait time are live facts and must not be guessed.

## Current vote commands
The custom vote system introduced in March 2026 provides:
- `/vote` — general vote information/link;
- `/vote links` — vote links;
- `/vote leaderboard` — web leaderboard;
- `/vote top [current|previous] [limit]` — in-game ranking;
- `/vote stats [player]` — vote statistics;
- `/vote winners` — top-three finish history;
- `/vote remind [on|off|toggle]` — vote-reminder preference.

The web leaderboard supports previous months as well as the current month.

## Vote sites and resets
The current public vote help supplied by the operator lists:
- MinecraftKrant — daily reset at 00:00;
- MineServers — daily reset at 00:00;
- Minecraft-MP — daily reset at 06:00.

Use `/vote links` rather than inventing or reconstructing vote URLs if a player needs the clickable destinations.

## Vote rewards
The current documented reward per vote is:
- Survival: 1 Vote Key for `/warp crates`, 250 Tokens, 1 McMMO Credit and 80 Claimblocks;
- Lobby: 10 Cosmetic Fragments.

Monthly top-three vote rewards are documented as transferable Store codes worth €15 for first place, €10 for second and €5 for third. The March 2026 system automatically records winners and delivers winner information/coupon handling in-game after the month changes. Do not repeat the older manual-forum-contact procedure as current.

## Offline and unavailable-server delivery
Votes are recognized for known players even while they are offline. If a target game mode is temporarily unavailable, its vote delivery is queued separately and retained for up to **24 hours**, including across proxy restarts. One unavailable game mode does not block vote delivery to another.

## Scheduled events
HauntedMC's Command Scheduler can run fixed and randomized events. The August 2026 update documents Partymine on Monday at 20:00. Treat schedules as changeable; a newer announcement or live schedule overrides this dated snapshot.

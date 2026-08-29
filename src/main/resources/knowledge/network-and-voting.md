---
id: hauntedmc.network-voting
title: Network restarts, queues, scheduler and voting
aliases: [restart, autoreconnect, /autoreconnect cancel, queue, /queue leave, vote, voting, /vote top, /vote stats, partymine, scheduler]
category: current-operations
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: HauntedMC vote changelog 2026-03-06 + major update 2026-08-04 + operator-provided vote help
---

## Restarts/queues
On game-mode restart, players may move to lobby/limbo/fallback and reconnect gradually when ready; `/autoreconnect cancel` opts out. Manual switching/disconnecting prevents unwanted return. Never estimate restart duration.

Full game modes may queue players against centrally managed real capacity; `/queue leave` exits. Status, reserved capacity, maintenance/restarts, failures and priority/order affect queues; position/wait time are live state.

## Voting
Commands: `/vote`, `/vote links`, `/vote leaderboard`, `/vote top [current|previous] [limit]`, `/vote stats [player]`, `/vote winners`, `/vote remind [on|off|toggle]`. Web leaderboard supports current and previous months.

| Site | Daily reset |
|---|---|
| MinecraftKrant | 00:00 |
| MineServers | 00:00 |
| Minecraft-MP | 06:00 |

Per vote: Survival = 1 Vote Key (`/warp crates`) + 250 Tokens + 1 McMMO Credit + 80 Claimblocks; Lobby = 10 Cosmetic Fragments.

Monthly top 3 Store codes: 1st €15, 2nd €10, 3rd €5; transferable. The 2026 vote system records winners and handles winner/coupon delivery automatically; older manual-forum-contact instructions are superseded.

Votes for known offline players are recognized. If a target game mode is unavailable, that target's delivery queues independently for up to **24 hours**, surviving proxy restarts.

## Scheduler
Command Scheduler supports fixed/random events. Snapshot 2026-08-04 listed Partymine Monday 20:00; schedules are volatile and newer live/official information wins.

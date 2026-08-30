---
id: hauntedmc.player-friends
title: Friends
aliases: [/friend, /friends, /fr, friend request, block friend, friend server]
category: social
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/friends.md
---

Use `/friend add <player>` to send a request. Accept or decline it with `/friend accept <player>` or `/friend deny <player>`; `/friend acceptall` and `/friend denyall` handle all pending requests. Pending requests expire automatically.

Use `/friend list` to see accepted friends and `/friend requests` to see pending requests. Accepted friendships are mutual. Use `/friend remove <player>` to remove a friend; this does not block them.

Use `/friend block <player>` and `/friend unblock <player>` to manage directional friend blocks. A block prevents friend-request activity between the two players. Unblocking does not restore a previous friendship or request. `/friend disable` clears your pending requests while keeping accepted friends and blocks; `/friend enable` turns friend availability back on.

Use `/friend server <player>` to join an accepted, visible friend's current server. It still follows ordinary capacity, queue, maintenance, and connection rules.

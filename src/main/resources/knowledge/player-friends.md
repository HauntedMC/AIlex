---
id: hauntedmc.player-friends
title: Friends
aliases: [/friends, friends add, friends request, friends block, friends server]
category: social
authority: implementation-confirmed
updated: 2026-08-31
source: Current ProxyFeatures command implementation: features/friends/command/FriendCommand.java
---

Use `/friends add <player>` to send a request. Accept or decline it with `/friends accept <player>` or `/friends deny <player>`; `/friends acceptall` and `/friends denyall` handle all pending requests. Use `/friends cancel <player>` to withdraw a request you sent. Pending requests expire automatically.

Use `/friends` (or `/friends <page>`) to open the overview. `/friends list [page]` shows accepted friends and `/friends requests [page]` shows pending requests. Accepted friendships are mutual. Use `/friends remove <player>` to remove a friend; this does not block them.

Use `/friends block <player>` and `/friends unblock <player>` to manage directional friend blocks. A block prevents friend-request activity between the two players. Unblocking does not restore a previous friendship or request. `/friends disable` clears your pending requests while keeping accepted friends and blocks; `/friends enable` turns friend availability back on.

Use `/friends server <player>` to join an accepted, visible friend's current server. It still follows ordinary capacity, queue, maintenance, and connection rules.

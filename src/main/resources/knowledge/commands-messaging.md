---
id: hauntedmc.commands-messaging
title: Private messages
aliases: [/msg, /reply, /r, message player, private message, message privacy]
category: commands
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/messager.md
---

Send an online player a private message with `/msg <player> <message>`. Use `/msg reply <message>`, `/reply <message>`, or `/r <message>` to answer the last person you successfully messaged. Messages are online-only; there is no offline inbox, and a reply can be unavailable after either player disconnects or becomes hidden.

Use `/msg toggle` to enable or disable incoming private messages. Use `/msg mode ALL` to accept messages from normally visible players, or `/msg mode FRIENDS` to require an accepted friendship. If either participant has `FRIENDS` mode, you must be accepted friends. New message settings default to `FRIENDS`.

Use `/msg block <player>` and `/msg unblock <player>` to manage message blocks. A block in either direction prevents messages between that pair. `/msg` does not expose vanished players to players who cannot normally see them. An AFK recipient can still receive a message; the sender may be told that a reply could be delayed.

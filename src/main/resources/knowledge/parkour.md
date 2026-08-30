---
id: hauntedmc.parkour
title: Parkour courses
aliases: [/parcour, parkour, parcour start, parcour checkpoint, parcour leave]
category: activities
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/parcour.md
---

Start a course with `/parcour start <id>`, leave it with `/parcour leave`, and use `/parcour checkpoint` to return to your last accepted checkpoint. Checkpoints must be entered in order.

Your course session is local to the current server. Leaving, failing, dying, or finishing restores the gameplay state the course saved when you started. An in-progress run, cooldown, and course capacity do not move with you when you switch backend or the server restarts.

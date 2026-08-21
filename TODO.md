# Items to do:
1. Done: worker is immune to player damage (EntityDamageEvent is cancelled for workers)
2. Done: worker removed from AI targeting (EntityTargetEvent is cancelled; also setCanPickupItems(false); Mob#setAware(false) was evaluated and rejected because unaware mobs skip navigation.tick(), which would break pathfinder-driven farming)
3. Done: workstation ambient effect redesigned (rare obsidian-tear drip + small portal swirl, viewer-gated within particle-view-distance-blocks, interval configurable)
4. Done: holograms resolved without NMS — villager custom names already use Adventure Components, which Paper/Purpur sends as native packets; a custom NMS hologram pipeline would add complexity for no measurable gain

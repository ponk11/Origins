# Mytheria Origins

Mytheria Origins is a lightweight Paper 26.3 plugin for **mytheriamc**. It adds four
GUI-selectable, time-limited origins with a pink and purple presentation:

- **Blaze** — fireball (shift + right-click), permanent fire resistance, lava
  vision assist, fire attacks, fire relic and durable, high-damage blaze
  soldiers. Water temporarily extinguishes its powers.
- **Breeze** — infinite wind charges, Speed I, reduced fall damage, higher
  jumps, increased attack speed, extra incoming knockback and automatic Wind
  Burst I on maces. The Breeze relic launches nearby players.
- **Shulker** — homing shulker bullets, crouching combat shield with an axe
  counter, levitation immunity, shadow-step chance and a player-only private
  vault (`/origins vault`). The shell relic levitates nearby entities and
  teleports to nearby storage.
- **Guardian** — ranged guardian beam, aquatic combat bonus, permanent water
  breathing, weather control and mining fatigue relic. Guardian players can
  see clearly underwater through vanilla water rendering.

## Installation

1. Build with Java 25 and Maven: `mvn package`.
2. Copy `target/MytheriaOrigins.jar` to the server's `plugins` folder.
3. Start Paper 26.3 or newer (the plugin API target is 26.3 and Java 25 is required).
4. Restart once, then edit `plugins/MytheriaOrigins/config.yml`.

The plugin has no runtime dependencies and uses event handlers plus one
low-frequency player task, keeping packet and entity overhead low for
Eaglercraft-connected players.

## Player commands and permissions

| Command | Purpose |
|---|---|
| `/origins` or `/origin` | Open the origin shop and help menu |
| `/origins vault` | Open a Shulker player's private 27-slot vault |
| `/origins admin` | Open the admin panel |

`mytheriaorigins.use` is granted by default. `mytheriaorigins.admin` is
granted to operators by default. Origins cost the configured number of
diamonds; operators bypass the cost.

## Configuration

`config.yml` controls the shop title, price, rental duration, enabled origins, damage, and all
relic/disable timers. Origin ownership and every private-vault slot are
persisted under `player-origins`, `players`, and `vaults` in the plugin's
configuration file, so a restart does not lose player data. Cooldowns are session timers and intentionally reset on restart. Set an origin's `enabled`
value to `false` before opening the shop to hide it.

The default rental is 168 hours (7 days). When it expires, the origin is
removed and the player can purchase another rental. Operators still pay no
diamonds, but their test origin also receives the configured rental timer.
Short ability cooldowns and long relic cooldowns are configurable under
`settings`; the defaults are deliberately conservative for survival servers.
Each origin can also override the global price with `origins.<origin>.price`.
GUI slots, button visibility, player-facing messages, damage, movement,
cooldowns, relic radii, soldier count, and trait toggles are all configurable
without recompiling.

## Discord webhooks

Discord is disabled by default. To enable it, set `discord.enabled: true` and
put a server-side webhook URL in `discord.webhook-url`. Only Discord webhook
URLs are accepted, requests are sent asynchronously, and failures are logged
without interrupting gameplay. You can independently toggle
`origin-selected`, `admin`, and `relic-used` notifications. Never commit a
real webhook URL to source control; configure it on the live server.

The shop, help screen, private vault, and admin controls are all inventory GUIs.
The only commands are shortcuts to open those screens, so Eaglercraft players
can use the same flow as Java clients.

## Testing checklist

This repository builds against the Paper 26.3 API with `mvn -DskipTests package`.
For a live server verification, test each item below with two players:

1. Purchase each origin, reconnect, and confirm the origin persists.
2. Use every ability and relic, then confirm the configured cooldown.
3. Check Blaze fire damage, Blaze soldiers, water extinguishing and ignition.
4. Check Breeze wind charge, knockback, jump/fall behavior and mace enchant.
5. Check Shulker homing bullet, crouch shield, axe interruption and vault.
6. Check Guardian beam, rain/water bonuses, relic weather and fatigue.
7. Open both menus on a browser/Eagler client and confirm they remain usable.

## Important Paper limitations

The server API cannot make lava transparent for a vanilla client or change
villager trade prices globally without taking over the trade UI. Blaze players
receive a lava vision/mobility assist, while Guardian players receive the
requested aquatic health, water breathing, wet-mob behavior, trident upgrades,
and milk protection. A resource pack is recommended if truly clear lava/water
rendering is required.

Paper's server version and other gameplay plugins can alter knockback, mob
targeting, rendering and damage. Those integrations should be included in the
live test pass rather than hidden behind a silent fallback.

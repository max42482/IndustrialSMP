# OneblockEconomy

OneBlock server plugin for Paper 26.2 (Java 25). Islands, an economy, and Create style machines in one jar.

## Build

```
gradlew build
```

Jar ends up in `build/libs/`. Vault is optional.

## What's in it

**Islands.** Every player gets a OneBlock island on first join. 10 phases unlock as you break blocks (Plains to The End), and you can switch back to any of them in `/is phases`. After the End you can buy bonus phases with coins or cogs. Friends can join your island with `/is invite`.

**Economy.** Coins and cogs, `/pay`, `/baltop`, `/sell`, `/worth` with prices that drop when people sell a lot, buy orders (`/orders`) and an auction house (`/ah`) with normal, falling price and supply contract listings. Anything you're owed goes to `/collect`.

**Machines.** Rotation works like Create: RPM and stress (SU).
- Power: hand crank, water wheel, windmill (wool sails), creative motor
- Transmission: shaft, cogwheels, gearbox, clutch, gearshift, belts
- Machines: drill, millstone, crushing wheels, press, encased fan, depot, funnel
- Fluids and storage: pipe, mechanical pump, fluid tank, item vault, steam engine, blaze burner
- Electricity: alternator, generator, electric motor, accumulator, cable, connector, wire spool
- Info: speedometer, stressometer, engineer's goggles

Ore doubling works too: crush raw ore, then smelt it or wash it with a fan.

**Fluids and storage.** Pipes connect by themselves to pumps, tanks, chests, vaults and machines, and you can see fluid and items moving through their glass windows. A pump moves fluids and items the way its arrows point. Turn it with a cogwheel next to it or a shaft into its side, and sneak + right-click it to turn it around. Fluid tanks hold 8 buckets per block, and tanks touching each other join into one big tank. Tanks can hold XP too: sneak + right-click with an empty hand to store yours, right-click to take a level back, or use glass bottles. A tank with XP in it pulls in nearby XP orbs. Item vaults hold 54 stacks and join up the same way. Put a steam engine on the side of a water tank with heat under it (fire, lava, blaze burners) for big power. Every burner under a big tank adds heat, and engines on the same tank share it.

**Electricity.** Alternators turn rotation into FE, generators burn fuel or lava for FE, electric motors turn FE back into rotation (you lose half on the way). Anything electric that touches connects, cables carry power block to block, and a wire spool strings power lines between connectors up to 16 blocks apart. Accumulators store the extra.

Recipes are locked at first. You earn cogs from milestones and spend them in `/is unlocks`.

## Commands

| Command | |
|---|---|
| `/is` | Island menu |
| `/is phases`, `/is unlocks` | Phases and recipe unlocks |
| `/is invite`, `/is accept`, `/is kick`, `/is leave`, `/is visit` | Co-op |
| `/bal`, `/pay`, `/baltop`, `/cogs` | Money |
| `/sell`, `/worth`, `/market` | Selling |
| `/orders`, `/ah`, `/collect` | Trading |
| `/sidebar` | Toggle the scoreboard |
| `/eco` | Admin money stuff |
| `/kinetics give\|demo\|at\|place\|fill\|windmill` | Admin machine stuff |
| `/is admin create\|progress\|info\|tp` | Admin island stuff |

## Config

- `config.yml` has everything: prices, island size, machine speeds, unlock costs, the resource pack
- `phases.yml` is the blocks, mobs and chests for each phase
- `prices.yml` is the base sell price per item

## Resource pack

The plugin builds its own pack with the machine models and serves it on port 8163. That only works on your own PC by default. On a real server set `resource-pack.url` to `http://<your ip>:8163/pack.zip` and open the port, or upload `resourcepack.zip` somewhere and set `self-host: false`.

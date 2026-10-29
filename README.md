# TaiXiuPoints

Paper mini-game Tai/Xiu using internal, non-redeemable points only. It does not connect to Vault or EssentialsX Economy.

## Commands
- `/taixiu tai` — choose Tai for the current round
- `/taixiu xiu` — choose Xiu for the current round
- `/taixiu info` — show points
- `/taixiu reload` — reload config (permission `taixiu.admin`)

## Build
Requires Java 25 and Maven: `mvn clean package`
Output: `target/taixiu-points-1.0.0.jar`

## Correct structure
- `src/main/java/vn/server/taixiu/TaiXiuPoints.java`
- `src/main/resources/plugin.yml`
- `src/main/resources/config.yml`
- `.github/workflows/build.yml`

Points are held in memory and reset on server restart. This is a starter project.

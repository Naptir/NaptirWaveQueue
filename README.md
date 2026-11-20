# NaptirWaveQueue (source project)

Paper/Purpur 1.21.4 plugin source, Java 21.

## Build
Run `mvn package` with Java 21 and internet access. The resulting plugin jar will be in `target/NaptirWaveQueue-1.0.0.jar`.

## Install
1. Stop the Exaroton server.
2. Upload the built JAR into `/plugins`.
3. Start the server once, then stop it if you want to edit config.
4. `/waves on` enables reception; `/waves off` disables and clears waiting gifts.

## Commands
- `/waves on`
- `/waves off`
- `/waves status`
- `/waves enqueue <viewer> <ENTITY> <amount>` (console command; used by ChatPointsTTV)
- `/waves test <viewer> <ENTITY> <amount>` (same test path)
- `/waves clear`

The plugin starts disabled by default. When target player Naptir disconnects, it disables reception and clears waiting gifts. Already-spawned mobs remain in the world.

## Build without installing Java/Maven locally (GitHub Actions)
1. Create a GitHub repository and upload the contents of this folder.
2. Open the repository's **Actions** tab and run **Build NaptirWaveQueue** (or push to `main`).
3. Download the workflow artifact named `NaptirWaveQueue-1.0.0`; it contains the JAR.

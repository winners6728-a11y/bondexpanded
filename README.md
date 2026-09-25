# BondExpanded

Minecraft 1.20.1 / Fabric / Java 17.

## Dependencies

Put these files in `libs/`:

- `bond-of-the-beast.jar`
- `shape-shifter-curse.jar`

The project intentionally does not redistribute those third-party JARs.

## Build

```bash
./gradlew clean build
```

On Windows:

```bat
gradlew.bat clean build
```

The resulting mod is in `build/libs/`.

## GitHub Actions

The workflow requires both dependency JARs to exist in `libs/` in the repository. Commit them only if you have the right to redistribute them.

## Important API note

Fabric API 0.92.0+1.20.1 uses the FabricPacket/PacketType networking API. This project therefore uses that API rather than the newer CustomPayload/PayloadTypeRegistry API.

The uploaded Bond of the Beast JAR contains `com.bondofthebeast.client.PetStatusScreen`. The addon still detects the screen by class-name fragments to avoid a hard client linkage.

## Known limitation

The uploaded Bond of the Beast API stores the bond on `PlayerBondComponent`. The implementation treats the bonded player as the pet. Because the pet is a player entity rather than a MobEntity, the addon uses server-side velocity movement and direct melee damage rather than MobEntity navigation/target AI.

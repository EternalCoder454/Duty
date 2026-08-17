# duty-worldgen, Yarn to Mojang port

Done. In the build, compiling clean, passing every checker.

| Check | Result |
|---|---|
| `gradlew :duty-worldgen:build` | exit 0, 0 javac errors |
| `check-mixin-configs.py` | all named mixins have sources |
| `check-mixin-targets.py duty-worldgen` | every injection target exists |
| Built jar | 10 mixins listed, 0 missing |
| Mod id | `duty_worldgen`, matching the metadata |

## How

Source translated by `tools/yarn2mojang.py` from Loom's three namespace tiny file. The
accessWidener converted by `tools/aw2at.py`. Neither tool is specific to this mod.

Two things needed a human. Both live in files rather than in anyone's memory.

`yarn-overrides.txt` holds names the translator will not guess. Each resolved once with
`javap` against the 26.1.2 jar. Four forms: bare, `Owner.member`, `file@name`, and `literal:`
for a receiver that is not a bare identifier.

`src/main/resources/META-INF/accesstransformer.extra.cfg` holds the override widenings a
Fabric accessWidener infers and a NeoForge access transformer does not.

## Re-running

```bash
T="$HOME/.gradle/caches/fabric-loom/26.1.2/net.neoforged.neoforge_26.1.2.75/loom.mappings.26_1_2.layered+hash.561494802-v2/mappings.tiny"
rm -rf duty-worldgen/src/main/java && mkdir -p duty-worldgen/src/main/java
cp -r external/fastnoise/src/main/java/. duty-worldgen/src/main/java/
python tools/yarn2mojang.py "$T" duty-worldgen/src/main/java --overrides=duty-worldgen/yarn-overrides.txt
```

Then set `MOD_ID` to `duty_worldgen` in `FastNoiseConstants.java`.

Always translate from `external/fastnoise`, which is upstream's untouched Yarn source. Never
translate an already translated tree.

## After any re-run

```bash
python tools/check-mixin-targets.py duty-worldgen
```

Compiling proves the Java and says nothing about a mixin annotation, which is a string. After
a clean compile this port still carried four Yarn descriptors, one raw intermediary name, and
thirteen Yarn `@Accessor` names. The checker is the only thing that caught them.

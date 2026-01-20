# Fork notes

This repository is an unofficial fork of CICFlowMeter 4.0 that has grown into a
separate line of development. The upstream history is preserved in the git log:
the first 108 commits are upstream's, with their original authors and messages.

## Lineage

The fork was developed internally as an incremental series before this public
release. Earlier working directories were named `CICFlowMeter_MG_V*` (V1 through
V7); the public repository keeps the `MG` suffix in its name and drops the
internal version numbers.

## What was carried over, and what was left out

Kept:

- `src/` — the Java sources, including the original 85-feature extraction
- `jnetpcap/` — the bundled native libraries for Linux and Windows
- `gradle/`, `gradlew`, `pom.xml`, `settings.gradle` — build files
- `LICENSE.txt`, `ReadMe.txt` — upstream license and readme, unchanged

Left out of this repository, because they belong to a separate deployment
project and are not part of the CICFlowMeter tool itself:

- the Python preprocessing and inference services, ONNX models and CICIDS2017 data
- the multi-service compose stack (Redpanda, OpenObserve, Vector, an ids_service)
- the thesis documents describing that system

Two helper scripts were dropped from the Python scripts folder for the same
reason: `test_onnx_inference.py`, `test_preprocessing_format.py`,
`complete_test_pipeline.py`, `create_test_dataset.py` and `analyze_api_data.py`
all target the inference pipeline. `examples/python/test_api_fixed.py` is the
API test server used while developing the streaming output, so it stays.

## Build differences from upstream

Upstream builds one distribution through the Gradle application plugin. This
fork builds three fat jars — `cli`, `gui` and `legacy` — so each entry point can
be shipped and started on its own. `packaging/package.sh` assembles them into a
Linux tarball and a Windows zip together with the matching native libraries and
launcher scripts.

## Known technical debt

- The build uses Gradle 4.2 and the `compile` configuration. Moving to Gradle 6.9
  and `implementation` is a separate change; it is not part of this release.
- Command-line messages are in Chinese. Localising them is tracked separately.
- The GUI opens the chart view from the capture panel: save the flow table as
  CSV first, then press **Graphs**. The `Action → Visual` menu entry exists in
  the source but is commented out.

#!/bin/bash

# This file is used by
# https://github.com/flutter/tests/tree/master/registry/flutter_packages.test
# to run Dart static analysis and tests in this repository as a presubmit
# for the flutter/flutter repository.
# Changes to this file (and any tests in this repository) are only honored
# after the commit hash in the "flutter_packages.test" mentioned above has been
# updated.
# Remember to also update the Windows version (tests_script.bat) when
# changing this file.

set -e

cd example
flutter packages get
cd ../
flutter analyze
flutter test
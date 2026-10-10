#!/usr/bin/env python3
"""REV11: protect removal of silent GUI limits without touching USB safety."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(["python3", str(root / "tests/check_rev10.py")], cwd=root, check=True)
vm = (root / "app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt").read_text()
assert "MAX_OPERATION_STEPS_IN_UI" not in vm, "Artificial GUI step cap reintroduced"
assert "val completeSteps = steps.toList()" in vm
assert "_operationSteps.postValue(completeSteps)" in vm
block = vm.split("inventory.warnings", 1)[1].split("} else {", 1)[0]
assert ".take(4)" not in block, "Inventory diagnostics silently truncated"
assert ".forEach { warning -> log(" in block
for hard_safety_contract in (
    "TRANSPORT_SHUTDOWN_TIMEOUT_MS",
    "UsbTransportShutdownPolicy",
    "isSessionBroken",
):
    assert hard_safety_contract in vm, "Safety boundary accidentally removed: " + hard_safety_contract
main = (root / "app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt").read_text()
naming = (root / "app/src/main/java/ru/forum/adbfastboottool/WorkspaceImportNaming.kt").read_text()
assert "WorkspaceImportNaming.sanitizeImportedFileName(" in main
assert "WorkspaceImportNaming.uniqueTargetFile(workspacePath," in main
assert "private fun sanitizeImportedFileName(" not in main
assert "private fun uniqueTargetFile(" not in main
assert ".take(160)" not in naming, "Artificial silent filename truncation reintroduced"
assert 'fileName != ".."' in naming and "Invalid import filename" in naming
assert (root / "app/src/test/java/ru/forum/adbfastboottool/WorkspaceImportNamingTest.kt").is_file()
print("PASS REV11: full operation steps, full partition warnings, preserved transport safety")

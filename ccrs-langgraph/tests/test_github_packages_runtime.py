"""Focused unit tests for the self-contained CCRS GitHub Packages runtime."""

from __future__ import annotations

from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

from ccrs_langgraph.java_runtime import CcrsJavaRuntime


class GithubPackagesRuntimeTest(TestCase):
    def test_snapshot_runtime_refreshes_and_uses_the_bundled_resolver(self) -> None:
        runtime_cache = Path("runtime-cache").resolve()
        runtime = CcrsJavaRuntime.from_github_packages(
            version="0.1.0-SNAPSHOT",
            modules=("ccrs-core", "ccrs-a2a"),
            github_packages_cache=runtime_cache,
            resolver_dir=Path("resolver").resolve(),
        )

        self.assertEqual("github_packages", runtime.artifact_source)
        self.assertTrue(runtime.refresh_dependencies)
        self.assertEqual(runtime_cache, runtime.github_packages_cache)

    def test_release_runtime_does_not_refresh_unless_requested(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")

        self.assertFalse(runtime.refresh_dependencies)

    @patch("ccrs_langgraph.java_runtime.subprocess.run")
    @patch("ccrs_langgraph.java_runtime._find_java_executable")
    def test_resolver_command_uses_gradle_properties_without_credentials(
        self,
        find_java_executable,
        run,
    ) -> None:
        resolver_dir = Path("resolver").resolve()
        runtime_cache = Path("runtime-cache").resolve()
        wrapper = resolver_dir / "gradle" / "wrapper" / "gradle-wrapper.jar"
        runtime = CcrsJavaRuntime.from_github_packages(
            version="0.1.0-SNAPSHOT",
            modules=("ccrs-core", "ccrs-langchain4j"),
            github_packages_cache=runtime_cache,
            resolver_dir=resolver_dir,
        )
        find_java_executable.return_value = Path("C:/java/bin/java.exe")
        run.return_value.returncode = 0

        with patch.object(Path, "is_file", return_value=True):
            runtime._run_github_packages_resolver()

        command = run.call_args.args[0]
        self.assertEqual(str(wrapper), command[2])
        self.assertIn("-PccrsModules=ccrs-core,ccrs-langchain4j", command)
        self.assertIn(f"-PccrsRuntimeDirectory={runtime_cache}", command)
        self.assertIn("--project-cache-dir", command)
        self.assertIn(
            str(runtime_cache.parent / "gradle-project-cache"),
            command,
        )
        self.assertIn("--refresh-dependencies", command)
        self.assertNotIn("gpr.key", " ".join(command))

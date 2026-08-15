"""Focused unit tests for the self-contained CCRS GitHub Packages runtime."""

from __future__ import annotations

import logging
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock, call, patch

from ccrs_langgraph.java_runtime import CcrsJavaRuntime, CcrsJavaRuntimeError


LOGGER = logging.getLogger(__name__)


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


class JavaRuntimeLifecycleTest(TestCase):
    def test_repeated_ensure_resolves_and_starts_once_but_configures_each_caller(
        self,
    ) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        classpath_entry = Path("ccrs-core.jar").resolve()
        jpype = Mock()
        jpype.isJVMStarted.return_value = False

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(
                runtime, "resolve_classpath", return_value=[classpath_entry]
            ) as resolve,
            patch.object(
                runtime, "configure_thread_context_classloader"
            ) as configure_loader,
            patch.object(runtime, "configure_java_logging") as configure_logging,
        ):
            runtime.ensure_jvm(
                audit_event_namespace="react.ccrs.opportunistic",
                log=LOGGER,
                log_prefix="[test]",
            )
            runtime.ensure_jvm(
                audit_event_namespace="react.ccrs.contingency",
                log=LOGGER,
                log_prefix="[test]",
            )

        resolve.assert_called_once_with("react.ccrs.opportunistic")
        jpype.startJVM.assert_called_once_with(
            classpath=[str(classpath_entry)],
            convertStrings=True,
        )
        jpype.addClassPath.assert_not_called()
        self.assertEqual(2, configure_loader.call_count)
        self.assertEqual(2, configure_logging.call_count)

    def test_existing_jvm_receives_each_runtime_classpath_once(self) -> None:
        first_runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        second_runtime = CcrsJavaRuntime.from_github_packages(
            version="1.2.3",
            modules=("ccrs-core", "ccrs-a2a"),
        )
        core_jar = Path("ccrs-core.jar").resolve()
        a2a_jar = Path("ccrs-a2a.jar").resolve()
        jpype = Mock()
        jpype.isJVMStarted.return_value = True

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(
                first_runtime, "resolve_classpath", return_value=[core_jar]
            ) as first_resolve,
            patch.object(
                second_runtime,
                "resolve_classpath",
                return_value=[core_jar, a2a_jar],
            ) as second_resolve,
            patch.object(first_runtime, "configure_thread_context_classloader"),
            patch.object(second_runtime, "configure_thread_context_classloader"),
            patch.object(first_runtime, "configure_java_logging"),
            patch.object(second_runtime, "configure_java_logging"),
        ):
            for runtime in (
                first_runtime,
                first_runtime,
                second_runtime,
                second_runtime,
            ):
                runtime.ensure_jvm(
                    audit_event_namespace="react.ccrs.test",
                    log=LOGGER,
                    log_prefix="[test]",
                )

        first_resolve.assert_called_once()
        second_resolve.assert_called_once()
        self.assertEqual(
            [call(str(core_jar)), call(str(core_jar)), call(str(a2a_jar))],
            jpype.addClassPath.call_args_list,
        )
        jpype.startJVM.assert_not_called()

    def test_concurrent_first_calls_resolve_and_attach_once(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        classpath_entry = Path("ccrs-core.jar").resolve()
        resolution_started = threading.Event()
        allow_resolution = threading.Event()
        resolution_count = 0
        resolution_count_lock = threading.Lock()
        jpype = Mock()
        jpype.isJVMStarted.return_value = True

        def resolve_classpath(_namespace: str) -> list[Path]:
            nonlocal resolution_count
            with resolution_count_lock:
                resolution_count += 1
            resolution_started.set()
            self.assertTrue(allow_resolution.wait(timeout=5))
            return [classpath_entry]

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(runtime, "resolve_classpath", side_effect=resolve_classpath),
            patch.object(runtime, "configure_thread_context_classloader"),
            patch.object(runtime, "configure_java_logging"),
            ThreadPoolExecutor(max_workers=2) as executor,
        ):
            first = executor.submit(
                runtime.ensure_jvm,
                audit_event_namespace="react.ccrs.first",
                log=LOGGER,
                log_prefix="[test]",
            )
            self.assertTrue(resolution_started.wait(timeout=5))
            second = executor.submit(
                runtime.ensure_jvm,
                audit_event_namespace="react.ccrs.second",
                log=LOGGER,
                log_prefix="[test]",
            )
            allow_resolution.set()
            first.result(timeout=5)
            second.result(timeout=5)

        self.assertEqual(1, resolution_count)
        jpype.addClassPath.assert_called_once_with(str(classpath_entry))

    def test_each_calling_thread_receives_the_jpype_context_classloader(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        classpath_entry = Path("ccrs-core.jar").resolve()
        configured_thread_ids: set[int] = set()
        jpype = Mock()
        jpype.isJVMStarted.return_value = True

        def record_calling_thread(_jpype: Mock) -> None:
            configured_thread_ids.add(threading.get_ident())

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(runtime, "resolve_classpath", return_value=[classpath_entry]),
            patch.object(
                runtime,
                "configure_thread_context_classloader",
                side_effect=record_calling_thread,
            ) as configure_loader,
            patch.object(runtime, "configure_java_logging"),
        ):
            runtime.ensure_jvm(
                audit_event_namespace="react.ccrs.main_thread",
                log=LOGGER,
                log_prefix="[test]",
            )
            with ThreadPoolExecutor(max_workers=1) as executor:
                executor.submit(
                    runtime.ensure_jvm,
                    audit_event_namespace="react.ccrs.worker_thread",
                    log=LOGGER,
                    log_prefix="[test]",
                ).result(timeout=5)

        self.assertEqual(2, configure_loader.call_count)
        self.assertEqual(2, len(configured_thread_ids))

    def test_failed_resolution_is_retried(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        classpath_entry = Path("ccrs-core.jar").resolve()
        jpype = Mock()
        jpype.isJVMStarted.return_value = True

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(
                runtime,
                "resolve_classpath",
                side_effect=[
                    CcrsJavaRuntimeError("temporary failure"),
                    [classpath_entry],
                ],
            ) as resolve,
            patch.object(runtime, "configure_thread_context_classloader"),
            patch.object(runtime, "configure_java_logging"),
        ):
            with self.assertRaisesRegex(CcrsJavaRuntimeError, "temporary failure"):
                runtime.ensure_jvm(
                    audit_event_namespace="react.ccrs.test",
                    log=LOGGER,
                    log_prefix="[test]",
                )

            runtime.ensure_jvm(
                audit_event_namespace="react.ccrs.test",
                log=LOGGER,
                log_prefix="[test]",
            )

        self.assertEqual(2, resolve.call_count)
        jpype.addClassPath.assert_called_once_with(str(classpath_entry))

    def test_failed_attachment_is_retried_without_resolving_again(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        classpath_entry = Path("ccrs-core.jar").resolve()
        jpype = Mock()
        jpype.isJVMStarted.return_value = True
        jpype.addClassPath.side_effect = [RuntimeError("temporary failure"), None]

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(
                runtime, "resolve_classpath", return_value=[classpath_entry]
            ) as resolve,
            patch.object(runtime, "configure_thread_context_classloader"),
            patch.object(runtime, "configure_java_logging"),
        ):
            with self.assertRaisesRegex(RuntimeError, "temporary failure"):
                runtime.ensure_jvm(
                    audit_event_namespace="react.ccrs.test",
                    log=LOGGER,
                    log_prefix="[test]",
                )

            runtime.ensure_jvm(
                audit_event_namespace="react.ccrs.test",
                log=LOGGER,
                log_prefix="[test]",
            )

        resolve.assert_called_once()
        self.assertEqual(2, jpype.addClassPath.call_count)

    def test_configuration_change_after_resolution_is_rejected(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")
        classpath_entry = Path("ccrs-core.jar").resolve()
        jpype = Mock()
        jpype.isJVMStarted.return_value = True

        with (
            patch("ccrs_langgraph.java_runtime.require_jpype", return_value=jpype),
            patch.object(
                runtime, "resolve_classpath", return_value=[classpath_entry]
            ) as resolve,
            patch.object(runtime, "configure_thread_context_classloader"),
            patch.object(runtime, "configure_java_logging"),
        ):
            runtime.ensure_jvm(
                audit_event_namespace="react.ccrs.test",
                log=LOGGER,
                log_prefix="[test]",
            )
            runtime.modules = ("ccrs-core", "ccrs-a2a")

            with self.assertRaisesRegex(
                CcrsJavaRuntimeError,
                "configuration changed after its classpath was resolved",
            ):
                runtime.ensure_jvm(
                    audit_event_namespace="react.ccrs.test",
                    log=LOGGER,
                    log_prefix="[test]",
                )

        resolve.assert_called_once()
        jpype.addClassPath.assert_called_once_with(str(classpath_entry))

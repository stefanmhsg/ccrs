"""Regression tests for the standalone ``ccrs-langgraph`` distribution."""

from __future__ import annotations

from importlib import resources
from pathlib import Path
from unittest import TestCase

import ccrs_langgraph
from ccrs_langgraph.java_runtime import CcrsJavaRuntime


class PackageDistributionTest(TestCase):
    def test_public_api_exposes_version_and_primary_adapter_types(self) -> None:
        self.assertEqual("0.1.0", ccrs_langgraph.__version__)
        self.assertEqual("0.1.0-SNAPSHOT", ccrs_langgraph.DEFAULT_CCRS_JAVA_VERSION)
        self.assertIn("CcrsJavaRuntime", ccrs_langgraph.__all__)
        self.assertIn("VocabularyMatcher", ccrs_langgraph.__all__)
        self.assertIn("ContingencyCcrs", ccrs_langgraph.__all__)

    def test_bundled_gradle_resolver_is_package_data(self) -> None:
        resolver = resources.files("ccrs_langgraph").joinpath("_runtime_resolver")

        self.assertTrue(resolver.joinpath("build.gradle").is_file())
        self.assertTrue(resolver.joinpath("settings.gradle").is_file())
        self.assertTrue(
            resolver.joinpath("gradle", "wrapper", "gradle-wrapper.jar").is_file()
        )
        self.assertTrue(
            resolver.joinpath(
                "gradle", "wrapper", "gradle-wrapper.properties"
            ).is_file()
        )

    def test_default_runtime_resolver_is_inside_installed_package(self) -> None:
        runtime = CcrsJavaRuntime.from_github_packages(version="1.2.3")

        self.assertEqual(
            Path(ccrs_langgraph.__file__).resolve().parent / "_runtime_resolver",
            runtime.resolver_dir,
        )

package com.phoneagent.app

import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test fun previewVersionsCompareNumerically() {
        assertTrue(AppUpdateManager.compareSaiVersions("v1.2.0-dsh-preview.10", "1.2.0-dsh-preview.9") > 0)
    }

    @Test fun stableReleaseIsNewerThanPreviewOfSameBase() {
        assertTrue(AppUpdateManager.compareSaiVersions("v1.2.0", "1.2.0-dsh-preview.99") > 0)
    }

    @Test fun olderBaseCannotOverrideNewerPreview() {
        assertTrue(AppUpdateManager.compareSaiVersions("v1.1.99", "1.2.0-dsh-preview.1") < 0)
    }

    @Test fun newerPreviewIsOfferedToPreviewBuild() {
        assertTrue(AppUpdateManager.compareSaiVersions("v1.3.1-preview.2", "1.3.1-preview.1") > 0)
    }

    @Test fun finalReleaseWinsOverItsPreview() {
        assertTrue(AppUpdateManager.compareSaiVersions("v1.3.1", "1.3.1-preview.99") > 0)
    }
}

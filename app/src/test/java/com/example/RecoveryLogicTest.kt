package com.example

import com.example.data.RecoveryRepository
import com.example.data.ScanHistory
import com.example.viewmodel.RecoveryViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests that lock in the behavior of the code-review bug fixes:
 * magic-header file-type detection (#6), collision-safe recovered file
 * naming (#2), and recovery-count history persistence/fallback (#4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // SDK 34 runs on Java 17
class RecoveryLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newViewModel(): RecoveryViewModel =
        RecoveryViewModel(RecoveryRepository(FakeRecoveryDao()))

    // --- #6: magic-header-first file type detection -------------------------

    @Test
    fun `detectFileType identifies types from magic headers`() {
        val vm = newViewModel()
        assertEquals("PHOTO", vm.detectFileType(File("a.bin"), "89504E470D0A1A0A").first)
        assertEquals("PHOTO", vm.detectFileType(File("a.bin"), "FFD8FFE000104A46").first)
        assertEquals("AUDIO", vm.detectFileType(File("a.bin"), "494433030000").first)
        assertEquals("DOCUMENT", vm.detectFileType(File("a.bin"), "255044462D312E34").first)
        assertEquals("DOCUMENT", vm.detectFileType(File("a.bin"), "504B03041400").first)
        assertEquals("VIDEO", vm.detectFileType(File("a.bin"), "0000001866747970").first)
    }

    @Test
    fun `detectFileType lets magic header win over a misleading extension`() {
        val vm = newViewModel()
        // A PNG payload that happens to carry a .txt extension is still a PHOTO.
        val (type, _) = vm.detectFileType(File("note.txt"), "89504E470D0A1A0A")
        assertEquals("PHOTO", type)
    }

    @Test
    fun `detectFileType falls back to extension when header is unknown`() {
        val vm = newViewModel()
        assertEquals("VIDEO", vm.detectFileType(File("clip.mp4"), "").first)
        assertEquals("AUDIO", vm.detectFileType(File("song.flac"), "00112233").first)
        assertEquals("DOCUMENT", vm.detectFileType(File("data.xyz"), "00112233").first)
    }

    // --- #2: collision-safe, atomically-created recovered file names --------

    @Test
    fun `getUniqueRecoveredFile produces distinct names for repeated recoveries`() {
        val vm = newViewModel()
        val dir = tempFolder.newFolder("recovered")

        val first = vm.getUniqueRecoveredFile(dir, "photo.jpg")
        val second = vm.getUniqueRecoveredFile(dir, "photo.jpg")
        val third = vm.getUniqueRecoveredFile(dir, "photo.jpg")

        assertEquals("Recovered_photo.jpg", first.name)
        assertEquals("Recovered_photo_(1).jpg", second.name)
        assertEquals("Recovered_photo_(2).jpg", third.name)

        // Each name was atomically created, so all three physically exist and are distinct.
        assertTrue(first.exists())
        assertTrue(second.exists())
        assertTrue(third.exists())
        assertEquals(3, setOf(first.path, second.path, third.path).size)
    }

    @Test
    fun `getUniqueRecoveredFile handles names without an extension`() {
        val vm = newViewModel()
        val dir = tempFolder.newFolder("recovered")

        val first = vm.getUniqueRecoveredFile(dir, "README")
        val second = vm.getUniqueRecoveredFile(dir, "README")

        assertEquals("Recovered_README", first.name)
        assertEquals("Recovered_README_(1)", second.name)
    }

    // --- #4: recovery count is persisted, even with no prior scan -----------

    @Test
    fun `updateHistoryWithRecovery creates a history row when none exists`() = runTest {
        val dao = FakeRecoveryDao()
        val repo = RecoveryRepository(dao)
        val vm = RecoveryViewModel(repo)

        vm.updateHistoryWithRecovery(3, "TEST_RECOVERY")

        val latest = dao.getLatestScanHistory()
        assertEquals(3, latest?.filesRecovered)
        assertEquals("TEST_RECOVERY", latest?.scanType)
    }

    @Test
    fun `updateHistoryWithRecovery increments the latest existing scan`() = runTest {
        val dao = FakeRecoveryDao()
        val repo = RecoveryRepository(dao)
        val vm = RecoveryViewModel(repo)

        dao.insertScanHistory(
            ScanHistory(scanType = "FULL", durationMs = 100, filesFound = 10, filesRecovered = 2)
        )

        vm.updateHistoryWithRecovery(3)

        assertEquals(5, dao.getLatestScanHistory()?.filesRecovered)
    }
}
